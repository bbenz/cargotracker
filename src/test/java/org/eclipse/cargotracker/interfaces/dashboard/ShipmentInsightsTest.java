package org.eclipse.cargotracker.interfaces.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.CHICAGO;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.HAMBURG;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.HONGKONG;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.NEWYORK;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.ROTTERDAM;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.SHANGHAI;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.Itinerary;
import org.eclipse.cargotracker.domain.model.cargo.Leg;
import org.eclipse.cargotracker.domain.model.cargo.RouteSpecification;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;
import org.eclipse.cargotracker.domain.model.handling.HandlingHistory;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.eclipse.cargotracker.domain.model.voyage.VoyageNumber;
import org.eclipse.cargotracker.interfaces.dashboard.ShipmentView.LegView;
import org.junit.jupiter.api.Test;

class ShipmentInsightsTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 11, 45);

  private static final Voyage ATLANTIC =
      new Voyage.Builder(new VoyageNumber("TA400"), HAMBURG)
          .addMovement(NEWYORK, hours(-216), hours(8))
          .build();

  private static final Voyage ASIA_EUROPE =
      new Voyage.Builder(new VoyageNumber("AE220"), SHANGHAI)
          .addMovement(ROTTERDAM, hours(-600), hours(-30))
          .build();

  private static final PortNotices.Notice ROTTERDAM_NOTICE =
      new PortNotices.Notice(
          "NLRTM", "Port notice, Rotterdam (NLRTM)", "Berths are full.", "notice-rotterdam.md");

  @Test
  void cargoAtSeaIsOnItsCurrentLegAndNeedsNoAttention() {
    Cargo cargo = cargo("HAM211", HAMBURG, NEWYORK, leg(ATLANTIC, HAMBURG, NEWYORK, -216, 8));
    List<HandlingEvent> events = new ArrayList<>();
    events.add(event(cargo, HandlingEvent.Type.RECEIVE, HAMBURG, null, -240));
    events.add(event(cargo, HandlingEvent.Type.LOAD, HAMBURG, ATLANTIC, -216));

    ShipmentView view = view(cargo, events);

    assertThat(view.status()).isEqualTo("ONBOARD_CARRIER");
    assertThat(view.currentLeg()).isZero();
    assertThat(view.legs()).extracting(LegView::state).containsExactly("CURRENT");
    assertThat(view.nextActivity().type()).isEqualTo("UNLOAD");
    assertThat(view.nextActivity().location()).isEqualTo("USNYC");
    assertThat(view.attention()).isNull();
  }

  @Test
  void lateArrivalPointsToThePortNotice() {
    Cargo cargo =
        cargo("SIN828", SHANGHAI, ROTTERDAM, leg(ASIA_EUROPE, SHANGHAI, ROTTERDAM, -600, -30));
    List<HandlingEvent> events = new ArrayList<>();
    events.add(event(cargo, HandlingEvent.Type.RECEIVE, SHANGHAI, null, -624));
    events.add(event(cargo, HandlingEvent.Type.LOAD, SHANGHAI, ASIA_EUROPE, -600));

    ShipmentView view = view(cargo, events);

    assertThat(view.attention().issue()).isEqualTo("LATE");
    assertThat(view.attention().summary()).isEqualTo("Arrival in Rotterdam is 30 hours late.");
    assertThat(view.attention().notice()).isEqualTo("notice-rotterdam.md");
  }

  @Test
  void unloadingOffTheRouteIsMisdirected() {
    Cargo cargo = cargo("HAM211", HAMBURG, NEWYORK, leg(ATLANTIC, HAMBURG, NEWYORK, -216, 8));
    List<HandlingEvent> events = new ArrayList<>();
    events.add(event(cargo, HandlingEvent.Type.RECEIVE, HAMBURG, null, -240));
    events.add(event(cargo, HandlingEvent.Type.LOAD, HAMBURG, ATLANTIC, -216));
    events.add(event(cargo, HandlingEvent.Type.UNLOAD, ROTTERDAM, ATLANTIC, -190));

    ShipmentView view = view(cargo, events);

    assertThat(view.status()).isEqualTo("MISDIRECTED");
    assertThat(view.location()).isEqualTo("NLRTM");
    assertThat(view.attention().level()).isEqualTo("critical");
    assertThat(view.attention().summary())
        .isEqualTo("Unloaded in Rotterdam, which is not on the planned route.");
  }

  @Test
  void bookingWithoutARouteIsNotRouted() {
    Cargo cargo =
        new Cargo(
            new TrackingId("BOM131"),
            new RouteSpecification(HONGKONG, ROTTERDAM, NOW.toLocalDate().plusDays(25)));
    cargo.deriveDeliveryProgress(new HandlingHistory(List.of()));

    ShipmentView view = ShipmentInsights.view(cargo, null, NOW, code -> Optional.empty());

    assertThat(view.status()).isEqualTo("NOT_ROUTED");
    assertThat(view.location()).isEqualTo("CNHKG");
    assertThat(view.attention().issue()).isEqualTo("NOT_ROUTED");
  }

  @Test
  void newDestinationOffTheRouteIsMisrouted() {
    Cargo cargo = cargo("HAM030", HAMBURG, CHICAGO, leg(ATLANTIC, HAMBURG, NEWYORK, -216, 8));
    List<HandlingEvent> events = new ArrayList<>();
    events.add(event(cargo, HandlingEvent.Type.RECEIVE, HAMBURG, null, -240));
    events.add(event(cargo, HandlingEvent.Type.LOAD, HAMBURG, ATLANTIC, -216));

    ShipmentView view = view(cargo, events);

    assertThat(view.status()).isEqualTo("MISROUTED");
    assertThat(view.currentLeg()).isZero();
    assertThat(view.attention().summary())
        .isEqualTo("The route ends in New York, but the booking now says Chicago.");
  }

  @Test
  void cargoIsExpectedAtThePortADayBeforeLoading() {
    Cargo cargo = cargo("HAM211", HAMBURG, NEWYORK, leg(ATLANTIC, HAMBURG, NEWYORK, -216, 8));
    cargo.deriveDeliveryProgress(new HandlingHistory(List.of()));

    ShipmentView view = ShipmentInsights.view(cargo, null, NOW, code -> Optional.empty());

    assertThat(view.status()).isEqualTo("NOT_RECEIVED");
    assertThat(view.nextActivity().type()).isEqualTo("RECEIVE");
    assertThat(view.legs()).extracting(LegView::state).containsExactly("NEXT");
    assertThat(view.attention().issue()).isEqualTo("LATE");
    assertThat(view.attention().summary()).isEqualTo("Receipt in Hamburg is 10 days late.");
  }

  private static ShipmentView view(Cargo cargo, List<HandlingEvent> events) {
    HandlingHistory history = new HandlingHistory(events);
    cargo.deriveDeliveryProgress(history);
    return ShipmentInsights.view(
        cargo,
        history.getMostRecentlyCompletedEvent(),
        NOW,
        code -> code.equals("NLRTM") ? Optional.of(ROTTERDAM_NOTICE) : Optional.empty());
  }

  private static Cargo cargo(String id, Location origin, Location destination, Leg leg) {
    Cargo cargo =
        new Cargo(
            new TrackingId(id),
            new RouteSpecification(origin, destination, NOW.toLocalDate().plusDays(5)));
    cargo.assignToRoute(new Itinerary(List.of(leg)));
    return cargo;
  }

  private static Leg leg(
      Voyage voyage, Location from, Location to, int loadHours, int unloadHours) {
    return new Leg(voyage, from, to, hours(loadHours), hours(unloadHours));
  }

  private static HandlingEvent event(
      Cargo cargo, HandlingEvent.Type type, Location location, Voyage voyage, int hours) {
    return voyage == null
        ? new HandlingEvent(cargo, hours(hours), hours(hours), type, location)
        : new HandlingEvent(cargo, hours(hours), hours(hours), type, location, voyage);
  }

  private static LocalDateTime hours(int hours) {
    return NOW.plusHours(hours);
  }
}
