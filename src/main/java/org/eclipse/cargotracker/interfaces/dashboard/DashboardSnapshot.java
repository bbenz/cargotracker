package org.eclipse.cargotracker.interfaces.dashboard;

import java.util.List;

/** Everything the dashboard page needs in one call. Times are epoch milliseconds. */
public record DashboardSnapshot(
    long generatedAt,
    List<PortView> ports,
    List<VoyageView> voyages,
    List<ShipmentView> shipments,
    List<PortNotices.Notice> notices,
    String traceUrl) {

  public record PortView(
      String code, String name, double latitude, double longitude, boolean inland) {}

  public record VoyageView(String number, List<Movement> movements) {}

  public record Movement(String from, String to, long departs, long arrives) {}
}
