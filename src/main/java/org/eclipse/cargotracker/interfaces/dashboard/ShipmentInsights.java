package org.eclipse.cargotracker.interfaces.dashboard;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.CargoRepository;
import org.eclipse.cargotracker.domain.model.cargo.Delivery;
import org.eclipse.cargotracker.domain.model.cargo.HandlingActivity;
import org.eclipse.cargotracker.domain.model.cargo.Leg;
import org.eclipse.cargotracker.domain.model.cargo.RouteSpecification;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;
import org.eclipse.cargotracker.domain.model.handling.HandlingEventRepository;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.eclipse.cargotracker.interfaces.booking.sse.RealtimeCargoTrackingViewAdapter;
import org.eclipse.cargotracker.interfaces.dashboard.ShipmentView.Activity;
import org.eclipse.cargotracker.interfaces.dashboard.ShipmentView.Attention;
import org.eclipse.cargotracker.interfaces.dashboard.ShipmentView.LegView;

/**
 * Builds read-only shipment views from the existing repositories. The dashboard and the assistant
 * tools both use this class, so they agree on what "late" and "needs attention" mean.
 */
@ApplicationScoped
public class ShipmentInsights {

  /** A planned activity counts as late this long after its planned time. */
  public static final Duration LATE_AFTER = Duration.ofHours(6);

  /** Cargo is expected at the origin port this long before the first voyage leaves. */
  public static final Duration RECEIVE_BEFORE_LOAD = Duration.ofHours(24);

  /** The customer is expected to pick up cargo this long after it arrives. */
  public static final Duration CLAIM_AFTER_UNLOAD = Duration.ofHours(24);

  @Inject private CargoRepository cargoRepository;
  @Inject private HandlingEventRepository handlingEventRepository;
  @Inject private PortNotices portNotices;

  public List<ShipmentView> shipments() {
    LocalDateTime now = LocalDateTime.now();
    return cargoRepository.findAll().stream()
        .map(cargo -> view(cargo, lastEvent(cargo), now, portNotices::forPort))
        .sorted(Comparator.comparing(ShipmentView::trackingId))
        .toList();
  }

  public Optional<ShipmentView> shipment(String trackingId) {
    if (trackingId == null || trackingId.isBlank()) {
      return Optional.empty();
    }
    Cargo cargo = cargoRepository.find(new TrackingId(trackingId.trim().toUpperCase(Locale.ROOT)));
    return Optional.ofNullable(cargo)
        .map(found -> view(found, lastEvent(found), LocalDateTime.now(), portNotices::forPort));
  }

  /** Cargo that is at a location now, or that will load, unload, or end its trip there. */
  public List<ShipmentView> atLocation(String location) {
    String wanted = location == null ? "" : location.trim().toLowerCase(Locale.ROOT);
    if (wanted.isEmpty()) {
      return List.of();
    }
    return shipments().stream().filter(shipment -> touches(shipment, wanted)).toList();
  }

  public static LocalDateTime plannedTime(Cargo cargo, HandlingActivity activity) {
    return plannedTime(legs(cargo), activity);
  }

  static ShipmentView view(
      Cargo cargo,
      HandlingEvent last,
      LocalDateTime now,
      Function<String, Optional<PortNotices.Notice>> notices) {
    Delivery delivery = cargo.getDelivery();
    RouteSpecification spec = cargo.getRouteSpecification();
    List<Leg> legs = legs(cargo);
    HandlingActivity next = delivery.getNextExpectedActivity();
    boolean hasNext = next != null && !next.isEmpty();
    LocalDateTime nextTime = hasNext ? plannedTime(legs, next) : null;
    Integer currentLeg = currentLeg(delivery, last, legs);
    Location location =
        delivery.getTransportStatus() == TransportStatus.NOT_RECEIVED
            ? cargo.getOrigin()
            : delivery.getLastKnownLocation();

    return new ShipmentView(
        cargo.getTrackingId().getIdString(),
        code(cargo.getOrigin()),
        cargo.getOrigin().getName(),
        code(spec.getDestination()),
        spec.getDestination().getName(),
        new RealtimeCargoTrackingViewAdapter(cargo).getStatusCode(),
        delivery.getRoutingStatus().name(),
        delivery.getTransportStatus().name(),
        delivery.isMisdirected(),
        code(location),
        location.getName(),
        voyageNumber(delivery.getCurrentVoyage()),
        last == null
            ? null
            : new Activity(
                last.getType().name(),
                code(last.getLocation()),
                last.getLocation().getName(),
                voyageNumber(last.getVoyage()),
                epoch(last.getCompletionTime())),
        hasNext
            ? new Activity(
                next.getType().name(),
                code(next.getLocation()),
                next.getLocation().getName(),
                voyageNumber(next.getVoyage()),
                epoch(nextTime))
            : null,
        epoch(delivery.getEstimatedTimeOfArrival()),
        spec.getArrivalDeadline() == null ? null : spec.getArrivalDeadline().toString(),
        currentLeg,
        legViews(legs, hasNext ? next : null, delivery, currentLeg, now),
        attention(spec, delivery, last, legs, hasNext ? next : null, nextTime, now, notices));
  }

  private static Attention attention(
      RouteSpecification spec,
      Delivery delivery,
      HandlingEvent last,
      List<Leg> legs,
      HandlingActivity next,
      LocalDateTime nextTime,
      LocalDateTime now,
      Function<String, Optional<PortNotices.Notice>> notices) {
    if (delivery.isMisdirected()) {
      return new Attention("critical", "MISDIRECTED", misdirectedSummary(last), null);
    }
    if (delivery.getRoutingStatus() == RoutingStatus.MISROUTED) {
      Location end = legs.isEmpty() ? null : legs.get(legs.size() - 1).getUnloadLocation();
      String summary =
          end != null && !end.sameIdentityAs(spec.getDestination())
              ? "The route ends in "
                  + end.getName()
                  + ", but the booking now says "
                  + spec.getDestination().getName()
                  + "."
              : "The route no longer meets the booking, for example the arrival deadline.";
      return new Attention("critical", "MISROUTED", summary, null);
    }
    if (delivery.getRoutingStatus() == RoutingStatus.NOT_ROUTED) {
      return new Attention("warning", "NOT_ROUTED", "Booked, but no route is assigned yet.", null);
    }
    if (next != null && nextTime != null && now.isAfter(nextTime.plus(LATE_AFTER))) {
      String place = next.getLocation().getName();
      String what =
          switch (next.getType()) {
            case LOAD -> "Loading in " + place;
            case UNLOAD -> "Arrival in " + place;
            case RECEIVE -> "Receipt in " + place;
            case CLAIM -> "Pickup in " + place;
            default -> next.getType().name() + " in " + place;
          };
      String notice =
          notices.apply(code(next.getLocation())).map(PortNotices.Notice::source).orElse(null);
      return new Attention(
          "warning",
          "LATE",
          what + " is " + lateness(Duration.between(nextTime, now)) + " late.",
          notice);
    }
    return null;
  }

  private static String misdirectedSummary(HandlingEvent last) {
    if (last == null) {
      return "Handled at a place that is not on the planned route.";
    }
    String place = last.getLocation().getName();
    if (last.getType() == HandlingEvent.Type.LOAD) {
      return "Loaded onto voyage "
          + voyageNumber(last.getVoyage())
          + " in "
          + place
          + ". That voyage is not on the planned route.";
    }
    if (last.getType() == HandlingEvent.Type.UNLOAD) {
      return "Unloaded in " + place + ", which is not on the planned route.";
    }
    return "Handled in " + place + ", which is not on the planned route.";
  }

  private static String lateness(Duration late) {
    long hours = late.toHours();
    return hours < 48 ? hours + " hours" : (hours / 24) + " days";
  }

  private static List<LegView> legViews(
      List<Leg> legs,
      HandlingActivity next,
      Delivery delivery,
      Integer currentLeg,
      LocalDateTime now) {
    int nextIndex = next == null ? -1 : legIndex(legs, next);
    List<LegView> views = new ArrayList<>();
    for (int i = 0; i < legs.size(); i++) {
      Leg leg = legs.get(i);
      String state;
      if (next != null && next.getType() == HandlingEvent.Type.RECEIVE) {
        state = "NEXT";
      } else if (next != null && next.getType() == HandlingEvent.Type.CLAIM) {
        state = "DONE";
      } else if (next != null && nextIndex >= 0) {
        boolean unloading = next.getType() == HandlingEvent.Type.UNLOAD;
        state = i < nextIndex ? "DONE" : (i == nextIndex && unloading ? "CURRENT" : "NEXT");
      } else if (delivery.getTransportStatus() == TransportStatus.CLAIMED) {
        state = "DONE";
      } else if (currentLeg != null) {
        state = i < currentLeg ? "DONE" : (i == currentLeg ? "CURRENT" : "NEXT");
      } else {
        state =
            !leg.getUnloadTime().isAfter(now)
                ? "DONE"
                : (!leg.getLoadTime().isAfter(now) ? "CURRENT" : "NEXT");
      }
      views.add(
          new LegView(
              voyageNumber(leg.getVoyage()),
              code(leg.getLoadLocation()),
              leg.getLoadLocation().getName(),
              code(leg.getUnloadLocation()),
              leg.getUnloadLocation().getName(),
              epoch(leg.getLoadTime()),
              epoch(leg.getUnloadTime()),
              state));
    }
    return views;
  }

  private static Integer currentLeg(Delivery delivery, HandlingEvent last, List<Leg> legs) {
    if (delivery.getTransportStatus() != TransportStatus.ONBOARD_CARRIER
        || last == null
        || last.getType() != HandlingEvent.Type.LOAD) {
      return null;
    }
    for (int i = 0; i < legs.size(); i++) {
      Leg leg = legs.get(i);
      if (leg.getLoadLocation().sameIdentityAs(last.getLocation())
          && sameVoyage(leg.getVoyage(), last.getVoyage())) {
        return i;
      }
    }
    return null;
  }

  private static int legIndex(List<Leg> legs, HandlingActivity activity) {
    for (int i = 0; i < legs.size(); i++) {
      Leg leg = legs.get(i);
      boolean sameVoyage = sameVoyage(leg.getVoyage(), activity.getVoyage());
      if (activity.getType() == HandlingEvent.Type.LOAD
          && sameVoyage
          && leg.getLoadLocation().sameIdentityAs(activity.getLocation())) {
        return i;
      }
      if (activity.getType() == HandlingEvent.Type.UNLOAD
          && sameVoyage
          && leg.getUnloadLocation().sameIdentityAs(activity.getLocation())) {
        return i;
      }
    }
    return -1;
  }

  static LocalDateTime plannedTime(List<Leg> legs, HandlingActivity activity) {
    if (activity == null || activity.isEmpty() || legs.isEmpty()) {
      return null;
    }
    return switch (activity.getType()) {
      case RECEIVE -> legs.get(0).getLoadTime().minus(RECEIVE_BEFORE_LOAD);
      case CLAIM -> legs.get(legs.size() - 1).getUnloadTime().plus(CLAIM_AFTER_UNLOAD);
      case LOAD, UNLOAD -> {
        int index = legIndex(legs, activity);
        if (index < 0) {
          yield null;
        }
        Leg leg = legs.get(index);
        yield activity.getType() == HandlingEvent.Type.LOAD
            ? leg.getLoadTime()
            : leg.getUnloadTime();
      }
      default -> null;
    };
  }

  private static boolean touches(ShipmentView shipment, String wanted) {
    boolean active = !"CLAIMED".equals(shipment.transportStatus());
    boolean inPlace =
        active
            && !"ONBOARD_CARRIER".equals(shipment.transportStatus())
            && matches(shipment.location(), shipment.locationName(), wanted);
    boolean onRoute =
        shipment.legs().stream()
            .filter(leg -> !"DONE".equals(leg.state()))
            .anyMatch(
                leg ->
                    matches(leg.from(), leg.fromName(), wanted)
                        || matches(leg.to(), leg.toName(), wanted));
    boolean endsThere =
        active && matches(shipment.destination(), shipment.destinationName(), wanted);
    return inPlace || onRoute || endsThere;
  }

  private static boolean matches(String code, String name, String wanted) {
    return code != null
        && (code.equalsIgnoreCase(wanted)
            || (name != null && name.toLowerCase(Locale.ROOT).contains(wanted)));
  }

  private HandlingEvent lastEvent(Cargo cargo) {
    return handlingEventRepository
        .lookupHandlingHistoryOfCargo(cargo.getTrackingId())
        .getMostRecentlyCompletedEvent();
  }

  private static List<Leg> legs(Cargo cargo) {
    return cargo.getItinerary() == null ? List.of() : cargo.getItinerary().getLegs();
  }

  private static boolean sameVoyage(Voyage first, Voyage second) {
    String a = voyageNumber(first);
    return a != null && a.equals(voyageNumber(second));
  }

  static String voyageNumber(Voyage voyage) {
    if (voyage == null || voyage.getVoyageNumber() == null) {
      return null;
    }
    String number = voyage.getVoyageNumber().getIdString();
    return number == null || number.isBlank() ? null : number;
  }

  static String code(Location location) {
    return location == null ? null : location.getUnLocode().getIdString();
  }

  static Long epoch(LocalDateTime time) {
    return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }
}
