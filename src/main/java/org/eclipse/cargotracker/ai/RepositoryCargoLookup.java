package org.eclipse.cargotracker.ai;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.cargotracker.interfaces.dashboard.PortNotices;
import org.eclipse.cargotracker.interfaces.dashboard.ShipmentInsights;
import org.eclipse.cargotracker.interfaces.dashboard.ShipmentView;

/** Answers tool calls from the same read model the dashboard uses. */
@ApplicationScoped
public class RepositoryCargoLookup implements CargoLookup {

  @Inject private ShipmentInsights insights;
  @Inject private PortNotices notices;
  @Inject private Procedures procedures;

  @Override
  public Optional<CargoStatusDetails> cargoStatus(String trackingId) {
    return insights.shipment(trackingId).map(this::toDetails);
  }

  @Override
  public List<CargoSummary> listCargos() {
    return insights.shipments().stream().map(this::toSummary).toList();
  }

  @Override
  public List<AttentionItem> needingAttention() {
    return insights.shipments().stream()
        .filter(shipment -> shipment.attention() != null)
        .map(
            shipment ->
                new AttentionItem(
                    shipment.trackingId(), shipment.attention().issue(), details(shipment)))
        .toList();
  }

  @Override
  public List<CargoSummary> atLocation(String location) {
    return insights.atLocation(location).stream().map(this::toSummary).toList();
  }

  @Override
  public boolean exists(String trackingId) {
    return insights.shipment(trackingId).isPresent();
  }

  @Override
  public Set<String> knownTrackingIds() {
    return insights.shipments().stream().map(ShipmentView::trackingId).collect(Collectors.toSet());
  }

  private CargoSummary toSummary(ShipmentView shipment) {
    return new CargoSummary(
        shipment.trackingId(),
        shipment.originName(),
        shipment.destinationName(),
        shipment.status());
  }

  private CargoStatusDetails toDetails(ShipmentView shipment) {
    return new CargoStatusDetails(
        shipment.trackingId(),
        shipment.originName(),
        shipment.destinationName(),
        shipment.routingStatus(),
        shipment.transportStatus(),
        shipment.locationName(),
        activity(shipment.nextActivity()),
        shipment.eta() == null ? "unknown" : time(shipment.eta()),
        shipment.arrivalDeadline(),
        shipment.misdirected(),
        shipment.attention() == null ? "none" : details(shipment),
        shipment.legs().stream().map(this::leg).toList());
  }

  /** The problem summary, the procedure that applies, and a port notice when there is one. */
  private String details(ShipmentView shipment) {
    ShipmentView.Attention attention = shipment.attention();
    StringBuilder details = new StringBuilder(attention.summary());
    if (attention.notice() != null) {
      notices.all().stream()
          .filter(notice -> notice.source().equals(attention.notice()))
          .findFirst()
          .ifPresent(
              notice ->
                  details
                      .append(' ')
                      .append(notice.title())
                      .append(" (")
                      .append(notice.source())
                      .append("): ")
                      .append(notice.summary()));
    }
    procedures.forIssue(attention.issue()).ifPresent(text -> details.append(' ').append(text));
    return details.toString();
  }

  private String activity(ShipmentView.Activity activity) {
    if (activity == null) {
      return "none";
    }
    String voyage = activity.voyage() == null ? "" : " on " + activity.voyage();
    String planned = activity.time() == null ? "" : ", planned " + time(activity.time());
    return activity.type() + " in " + activity.locationName() + voyage + planned;
  }

  private String leg(ShipmentView.LegView leg) {
    return leg.fromName()
        + " to "
        + leg.toName()
        + " on "
        + leg.voyage()
        + ", "
        + time(leg.loadTime())
        + " to "
        + time(leg.unloadTime())
        + " ("
        + leg.state().toLowerCase()
        + ")";
  }

  private static String time(long epochMillis) {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .truncatedTo(ChronoUnit.MINUTES)
        .toString();
  }
}
