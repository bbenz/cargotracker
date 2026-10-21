package org.eclipse.cargotracker.demo;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.eclipse.cargotracker.application.ApplicationEvents;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.CargoRepository;
import org.eclipse.cargotracker.domain.model.cargo.HandlingActivity;
import org.eclipse.cargotracker.domain.model.voyage.VoyageNumber;
import org.eclipse.cargotracker.interfaces.dashboard.ShipmentInsights;
import org.eclipse.cargotracker.interfaces.handling.HandlingEventRegistrationAttempt;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Live traffic for the demo. At a steady pace, it reports the next planned handling event, the same
 * way a port system would. The report goes through the existing registration path: a JMS message,
 * the handling event service, cargo inspection, and the server-sent events that update the
 * dashboard.
 */
@Singleton
@Startup
public class DemoTraffic {

  /** Only events planned within this window are reported, so cargo does not jump weeks ahead. */
  private static final Duration LOOKAHEAD = Duration.ofHours(72);

  private static final long RECENT_MILLIS = 15_000;

  @Inject private Logger logger;
  @Inject private CargoRepository cargoRepository;
  @Inject private ApplicationEvents applicationEvents;

  private final Map<String, Long> recentlyReported = new ConcurrentHashMap<>();
  private volatile boolean enabled;
  private volatile int intervalSeconds;
  private volatile long lastStepAt;
  private volatile String lastReport;

  @PostConstruct
  void init() {
    Config config = ConfigProvider.getConfig();
    enabled = config.getOptionalValue("demo.traffic.enabled", Boolean.class).orElse(false);
    intervalSeconds =
        Math.max(
            2, config.getOptionalValue("demo.traffic.interval-seconds", Integer.class).orElse(20));
  }

  @Schedule(second = "*/2", minute = "*", hour = "*", persistent = false)
  public void tick() {
    if (enabled && System.currentTimeMillis() - lastStepAt >= intervalSeconds * 1000L) {
      step();
    }
  }

  /** Reports the next planned handling event, if one is due within the lookahead window. */
  public Optional<String> step() {
    lastStepAt = System.currentTimeMillis();
    recentlyReported.values().removeIf(at -> lastStepAt - at > RECENT_MILLIS);
    LocalDateTime now = LocalDateTime.now();

    Cargo next = null;
    LocalDateTime nextTime = null;
    for (Cargo cargo : cargoRepository.findAll()) {
      String trackingId = cargo.getTrackingId().getIdString();
      HandlingActivity activity = cargo.getDelivery().getNextExpectedActivity();
      if (DemoFleet.HELD.contains(trackingId)
          || recentlyReported.containsKey(trackingId)
          || activity == null
          || activity.isEmpty()) {
        continue;
      }
      LocalDateTime planned = ShipmentInsights.plannedTime(cargo, activity);
      if (planned == null || planned.isAfter(now.plus(LOOKAHEAD))) {
        continue;
      }
      if (nextTime == null || planned.isBefore(nextTime)) {
        next = cargo;
        nextTime = planned;
      }
    }
    if (next == null) {
      return Optional.empty();
    }

    HandlingActivity activity = next.getDelivery().getNextExpectedActivity();
    VoyageNumber voyage =
        activity.getType().requiresVoyage() ? activity.getVoyage().getVoyageNumber() : null;
    applicationEvents.receivedHandlingEventRegistrationAttempt(
        new HandlingEventRegistrationAttempt(
            now,
            now,
            next.getTrackingId(),
            voyage,
            activity.getType(),
            activity.getLocation().getUnLocode()));

    String trackingId = next.getTrackingId().getIdString();
    recentlyReported.put(trackingId, lastStepAt);
    lastReport =
        trackingId
            + ": "
            + activity.getType()
            + " in "
            + activity.getLocation().getName()
            + (voyage == null ? "" : " on " + voyage.getIdString());
    logger.info("Demo traffic reported " + lastReport);
    return Optional.of(lastReport);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getIntervalSeconds() {
    return intervalSeconds;
  }

  public String getLastReport() {
    return lastReport;
  }
}
