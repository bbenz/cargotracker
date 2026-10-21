package org.eclipse.cargotracker.ai;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The four sample cargo from Cargo Tracker, without a database. */
class FakeCargoLookup implements CargoLookup {

  private static final String NONE = "none";

  private final Map<String, CargoStatusDetails> cargos;

  FakeCargoLookup() {
    cargos =
        Map.of(
            "ABC123",
            details(
                "ABC123",
                "Hong Kong",
                "Helsinki",
                "ROUTED",
                "IN_PORT",
                "New York",
                "LOAD in New York on 0200T",
                false,
                NONE),
            "JKL567",
            details(
                "JKL567",
                "Hangzhou",
                "Stockholm",
                "ROUTED",
                "ONBOARD_CARRIER",
                "New York",
                NONE,
                true,
                "Loaded onto voyage 0100S in New York. That voyage is not on the planned route."),
            "DEF789",
            details(
                "DEF789",
                "Hong Kong",
                "Melbourne",
                "NOT_ROUTED",
                "NOT_RECEIVED",
                "Hong Kong",
                NONE,
                false,
                "Booked, but no route is assigned yet."),
            "MNO456",
            details(
                "MNO456", "New York", "Dallas", "ROUTED", "CLAIMED", "Dallas", NONE, false, NONE));
  }

  @Override
  public Optional<CargoStatusDetails> cargoStatus(String trackingId) {
    return Optional.ofNullable(trackingId == null ? null : cargos.get(trackingId.trim()));
  }

  @Override
  public List<CargoSummary> listCargos() {
    return cargos.values().stream().map(FakeCargoLookup::summary).toList();
  }

  @Override
  public List<AttentionItem> needingAttention() {
    return cargos.values().stream()
        .filter(cargo -> !NONE.equals(cargo.attention()))
        .map(cargo -> new AttentionItem(cargo.trackingId(), "ATTENTION", cargo.attention()))
        .toList();
  }

  @Override
  public List<CargoSummary> atLocation(String location) {
    String wanted = location == null ? "" : location.toLowerCase(Locale.ROOT);
    return cargos.values().stream()
        .filter(
            cargo ->
                cargo.origin().toLowerCase(Locale.ROOT).contains(wanted)
                    || cargo.destination().toLowerCase(Locale.ROOT).contains(wanted)
                    || cargo.lastKnownLocation().toLowerCase(Locale.ROOT).contains(wanted))
        .map(FakeCargoLookup::summary)
        .toList();
  }

  @Override
  public boolean exists(String trackingId) {
    return trackingId != null && cargos.containsKey(trackingId);
  }

  @Override
  public Set<String> knownTrackingIds() {
    return cargos.keySet();
  }

  private static CargoSummary summary(CargoStatusDetails cargo) {
    return new CargoSummary(
        cargo.trackingId(), cargo.origin(), cargo.destination(), cargo.transportStatus());
  }

  private static CargoStatusDetails details(
      String trackingId,
      String origin,
      String destination,
      String routingStatus,
      String transportStatus,
      String lastKnownLocation,
      String nextExpectedActivity,
      boolean misdirected,
      String attention) {
    return new CargoStatusDetails(
        trackingId,
        origin,
        destination,
        routingStatus,
        transportStatus,
        lastKnownLocation,
        nextExpectedActivity,
        "unknown",
        "unknown",
        misdirected,
        attention,
        List.of(origin + " to " + destination));
  }
}
