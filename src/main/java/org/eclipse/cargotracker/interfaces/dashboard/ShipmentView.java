package org.eclipse.cargotracker.interfaces.dashboard;

import java.util.List;

/** One cargo, as the dashboard and the assistant tools see it. Times are epoch milliseconds. */
public record ShipmentView(
    String trackingId,
    String origin,
    String originName,
    String destination,
    String destinationName,
    String status,
    String routingStatus,
    String transportStatus,
    boolean misdirected,
    String location,
    String locationName,
    String voyage,
    Activity lastEvent,
    Activity nextActivity,
    Long eta,
    String arrivalDeadline,
    Integer currentLeg,
    List<LegView> legs,
    Attention attention) {

  /** A handling event that happened, or the next one the plan expects. */
  public record Activity(
      String type, String location, String locationName, String voyage, Long time) {}

  public record LegView(
      String voyage,
      String from,
      String fromName,
      String to,
      String toName,
      long loadTime,
      long unloadTime,
      String state) {}

  /** Why the cargo needs attention. Level is "critical" or "warning". */
  public record Attention(String level, String issue, String summary, String notice) {}
}
