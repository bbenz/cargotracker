package org.eclipse.cargotracker.ai;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The four sample cargo from Cargo Tracker, without a database. Dates follow SampleDataGenerator,
 * so questions about deadlines and ETAs have real answers.
 */
class FakeCargoLookup implements CargoLookup {

  private static final String NONE = "none";
  private static final String UNKNOWN = "unknown";

  private final Map<String, CargoStatusDetails> cargos;

  FakeCargoLookup() {
    LocalDate today = LocalDate.now();
    cargos =
        Map.of(
            "ABC123",
            new CargoStatusDetails(
                "ABC123",
                "Hong Kong",
                "Helsinki",
                "ROUTED",
                "IN_PORT",
                "New York",
                "LOAD in New York on 0200T",
                today.plusDays(14).atTime(12, 0).toString(),
                today.plusDays(15).toString(),
                false,
                NONE,
                List.of(
                    "Hong Kong to New York on 0100S (done)",
                    "New York to Dallas on 0200T (next)",
                    "Dallas to Helsinki on 0300A (next)")),
            "JKL567",
            new CargoStatusDetails(
                "JKL567",
                "Hangzhou",
                "Stockholm",
                "ROUTED",
                "ONBOARD_CARRIER",
                "New York",
                NONE,
                UNKNOWN,
                today.plusDays(18).toString(),
                true,
                "Loaded onto voyage 0100S in New York. That voyage is not on the planned route.",
                List.of(
                    "Hangzhou to New York on 0100S (done)",
                    "New York to Dallas on 0200T (next)",
                    "Dallas to Stockholm on 0300A (next)")),
            "DEF789",
            new CargoStatusDetails(
                "DEF789",
                "Hong Kong",
                "Melbourne",
                "NOT_ROUTED",
                "NOT_RECEIVED",
                "Hong Kong",
                NONE,
                UNKNOWN,
                today.plusMonths(2).toString(),
                false,
                "Booked, but no route is assigned yet.",
                List.of()),
            "MNO456",
            new CargoStatusDetails(
                "MNO456",
                "New York",
                "Dallas",
                "ROUTED",
                "CLAIMED",
                "Dallas",
                NONE,
                today.minusDays(28).atTime(12, 0).toString(),
                today.minusDays(24).toString(),
                false,
                NONE,
                List.of("New York to Dallas on 0200T (done)")));
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
}
