package org.eclipse.cargotracker.demo;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.DependsOn;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.CargoRepository;
import org.eclipse.cargotracker.domain.model.cargo.Itinerary;
import org.eclipse.cargotracker.domain.model.cargo.Leg;
import org.eclipse.cargotracker.domain.model.cargo.RouteSpecification;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import org.eclipse.cargotracker.domain.model.handling.CannotCreateHandlingEventException;
import org.eclipse.cargotracker.domain.model.handling.HandlingEvent;
import org.eclipse.cargotracker.domain.model.handling.HandlingEventFactory;
import org.eclipse.cargotracker.domain.model.handling.HandlingEventRepository;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.LocationRepository;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.eclipse.cargotracker.domain.model.voyage.VoyageNumber;
import org.eclipse.cargotracker.interfaces.dashboard.ShipmentInsights;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Adds sample voyages and cargo on the main trade lanes, so the dashboard has a world to show. It
 * uses the normal domain objects and the HandlingEventFactory, the same way SampleDataGenerator
 * does. Times are relative to startup. Set DEMO_FLEET_ENABLED=false to turn it off.
 */
@Singleton
@Startup
@DependsOn("SampleDataGenerator")
public class DemoFleet {

  /** Cargo that live traffic leaves alone, so it stays late during the demo. */
  public static final Set<String> HELD = Set.of("SIN828", "SHA929");

  private static final String[][] NEW_LOCATIONS = {
    {"SGSIN", "Singapore"},
    {"USLAX", "Los Angeles"},
    {"AEJEA", "Jebel Ali"},
    {"BRSSZ", "Santos"},
    {"KRPUS", "Busan"},
    {"INBOM", "Mumbai"},
    {"ZADUR", "Durban"},
    {"ESALG", "Algeciras"}
  };

  @Inject private Logger logger;
  @PersistenceContext private EntityManager entityManager;
  @Inject private LocationRepository locationRepository;
  @Inject private CargoRepository cargoRepository;
  @Inject private HandlingEventFactory handlingEventFactory;
  @Inject private HandlingEventRepository handlingEventRepository;

  private final Map<String, Location> locations = new HashMap<>();
  private final Map<String, VoyagePlan> voyages = new HashMap<>();
  private LocalDateTime now;

  @PostConstruct
  @TransactionAttribute(TransactionAttributeType.REQUIRED)
  public void load() {
    boolean enabled =
        ConfigProvider.getConfig()
            .getOptionalValue("demo.fleet.enabled", Boolean.class)
            .orElse(true);
    if (!enabled || cargoRepository.find(new TrackingId("SHA101")) != null) {
      return;
    }
    logger.info("Loading the demo fleet.");
    now = LocalDateTime.now();
    loadLocations();
    loadVoyages();
    loadCargo();
  }

  private void loadLocations() {
    for (String[] location : NEW_LOCATIONS) {
      entityManager.persist(new Location(new UnLocode(location[0]), location[1]));
    }
    entityManager.flush();
    locationRepository
        .findAll()
        .forEach(location -> locations.put(location.getUnLocode().getIdString(), location));
  }

  private void loadVoyages() {
    // Hours from startup. A negative number is in the past.
    voyage(
        "AE210",
        start("CNSHA", -720),
        call("SGSIN", -600, -576),
        call("NLRTM", 2, 24),
        end("DEHAM", 40));
    voyage("AE220", start("SGSIN", -600), end("NLRTM", -30));
    voyage(
        "AE260",
        start("DEHAM", -384),
        call("NLRTM", -360, -348),
        call("AEJEA", -72, -60),
        call("SGSIN", 120, 132),
        end("CNHKG", 216));
    voyage(
        "AE310",
        start("KRPUS", -432),
        call("CNSHA", -396, -384),
        call("CNHKG", -336, -324),
        call("SGSIN", -240, -228),
        call("ESALG", 144, 156),
        end("NLRTM", 240));
    voyage("TP120", start("CNSHA", -144), call("KRPUS", -108, -96), end("USLAX", 192));
    voyage("TP130", start("CNSHA", -40), end("USLAX", 260));
    voyage("TP150", start("CNHKG", -336), call("JNTKO", -240, -228), end("USLAX", 5));
    voyage("RL300", start("USLAX", 216), end("USCHI", 312));
    voyage("RL310", start("USLAX", -24), end("USDAL", 48));
    voyage("RL320", start("USLAX", 30), end("USDAL", 96));
    voyage("RL400", start("USNYC", 30), end("USCHI", 60));
    voyage("TA400", start("DEHAM", -216), call("NLRTM", -192, -180), end("USNYC", 8));
    voyage("TA410", start("USNYC", -72), call("NLRTM", 144, 156), end("DEHAM", 180));
    voyage("ME500", start("INBOM", -192), call("AEJEA", -120, -96), end("ESALG", 216));
    voyage("SA600", start("BRSSZ", -168), call("ESALG", 120, 132), end("NLRTM", 216));
    voyage("AF700", start("ZADUR", -240), call("SGSIN", 48, 60), end("CNHKG", 144));
    voyage(
        "OC800",
        start("CNSHA", -288),
        call("CNHKG", -240, -228),
        call("SGSIN", -132, -120),
        end("AUMEL", 144));
    voyage(
        "IA900",
        start("CNHGH", 3),
        call("CNSHA", 20, 30),
        call("KRPUS", 66, 78),
        end("JNTKO", 114));
    voyage(
        "NE950",
        start("DEHAM", -36),
        call("SEGOT", -12, 6),
        call("SESTO", 36, 48),
        end("FIHEL", 66));
    entityManager.flush();
  }

  private void loadCargo() {
    // Tracking ID, origin, destination, arrival deadline in days from today, and legs.
    cargo("SHA101", "CNSHA", "NLRTM", 5, leg("AE210", "CNSHA", "NLRTM"));
    cargo("SIN202", "SGSIN", "DEHAM", 6, leg("AE210", "SGSIN", "DEHAM"));
    cargo("SHA725", "CNSHA", "SGSIN", -24, leg("AE210", "CNSHA", "SGSIN"));
    cargo("PUS404", "KRPUS", "NLRTM", 14, leg("AE310", "KRPUS", "NLRTM"));
    cargo("HKG505", "CNHKG", "ESALG", 9, leg("AE310", "CNHKG", "ESALG"));
    cargo(
        "SHA606",
        "CNSHA",
        "USCHI",
        16,
        leg("TP120", "CNSHA", "USLAX"),
        leg("RL300", "USLAX", "USCHI"));
    cargo("PUS707", "KRPUS", "USLAX", 10, leg("TP120", "KRPUS", "USLAX"));
    cargo(
        "HKG808",
        "CNHKG",
        "USDAL",
        7,
        leg("TP150", "CNHKG", "USLAX"),
        leg("RL320", "USLAX", "USDAL"));
    cargo("TYO909", "JNTKO", "USLAX", 3, leg("TP150", "JNTKO", "USLAX"));
    cargo("LAX110", "USLAX", "USDAL", 4, leg("RL310", "USLAX", "USDAL"));
    cargo("HAM211", "DEHAM", "USNYC", 3, leg("TA400", "DEHAM", "USNYC"));
    cargo(
        "RTM312",
        "NLRTM",
        "USCHI",
        6,
        leg("TA400", "NLRTM", "USNYC"),
        leg("RL400", "USNYC", "USCHI"));
    cargo("NYC413", "USNYC", "DEHAM", 10, leg("TA410", "USNYC", "DEHAM"));
    cargo("BOM514", "INBOM", "ESALG", 12, leg("ME500", "INBOM", "ESALG"));
    cargo("DXB615", "AEJEA", "ESALG", 11, leg("ME500", "AEJEA", "ESALG"));
    cargo("SSZ716", "BRSSZ", "NLRTM", 12, leg("SA600", "BRSSZ", "NLRTM"));
    cargo("DUR817", "ZADUR", "CNHKG", 9, leg("AF700", "ZADUR", "CNHKG"));
    cargo("SHA918", "CNSHA", "AUMEL", 8, leg("OC800", "CNSHA", "AUMEL"));
    cargo("HKG826", "CNHKG", "SGSIN", -4, leg("OC800", "CNHKG", "SGSIN"));
    cargo("HGH119", "CNHGH", "JNTKO", 7, leg("IA900", "CNHGH", "JNTKO"));
    cargo("GOT321", "SEGOT", "FIHEL", 5, leg("NE950", "SEGOT", "FIHEL"));
    cargo("HAM422", "DEHAM", "SESTO", 4, leg("NE950", "DEHAM", "SESTO"));
    cargo("HAM259", "DEHAM", "SEGOT", 3, leg("NE950", "DEHAM", "SEGOT"));
    cargo("RTM267", "NLRTM", "DEHAM", 4, leg("AE210", "NLRTM", "DEHAM"));
    cargo("RTM523", "NLRTM", "SGSIN", 9, leg("AE260", "NLRTM", "SGSIN"));
    cargo("SIN232", "SGSIN", "CNHKG", 12, leg("AE260", "SGSIN", "CNHKG"));
    cargo("LAX333", "USLAX", "USCHI", 15, leg("RL300", "USLAX", "USCHI"));
    cargo("NYC268", "USNYC", "USCHI", 5, leg("RL400", "USNYC", "USCHI"));
    cargo("LAX269", "USLAX", "USDAL", 6, leg("RL320", "USLAX", "USDAL"));
    // Late: the ship waits outside Rotterdam. See notice-rotterdam-berths.md.
    held("SIN828", 2, "SGSIN", "NLRTM", 4, leg("AE220", "SGSIN", "NLRTM"));
    // Late: departures from Shanghai have stopped. See notice-shanghai-typhoon.md.
    held("SHA929", 1, "CNSHA", "USLAX", 14, leg("TP130", "CNSHA", "USLAX"));
    // Misrouted: the customer changed the destination to Chicago. The route still ends in New York.
    cargo("HAM030", "DEHAM", "USCHI", 12, leg("TA400", "DEHAM", "USNYC"));
    // Booked, but not routed yet.
    cargo("BOM131", "INBOM", "NLRTM", 25);
  }

  private void cargo(String trackingId, String origin, String destination, int days, Leg... legs) {
    held(trackingId, Integer.MAX_VALUE, origin, destination, days, legs);
  }

  /** Creates cargo and records the handling events that are due by now, up to maxEvents. */
  private void held(
      String id, int maxEvents, String origin, String destination, int days, Leg... legs) {
    TrackingId trackingId = new TrackingId(id);
    Cargo cargo =
        new Cargo(
            trackingId,
            new RouteSpecification(
                location(origin), location(destination), LocalDate.now().plusDays(days)));
    if (legs.length > 0) {
      cargo.assignToRoute(new Itinerary(Arrays.asList(legs)));
    }
    entityManager.persist(cargo);

    int recorded = 0;
    for (PlannedEvent event : plannedEvents(legs)) {
      if (event.time().isAfter(now) || recorded == maxEvents) {
        break;
      }
      try {
        entityManager.persist(
            handlingEventFactory.createHandlingEvent(
                event.time(),
                event.time(),
                trackingId,
                event.voyage(),
                new UnLocode(event.location()),
                event.type()));
        recorded++;
      } catch (CannotCreateHandlingEventException e) {
        throw new IllegalStateException("Could not create a demo handling event for " + id, e);
      }
    }

    cargo.deriveDeliveryProgress(handlingEventRepository.lookupHandlingHistoryOfCargo(trackingId));
    entityManager.persist(cargo);
  }

  private List<PlannedEvent> plannedEvents(Leg... legs) {
    List<PlannedEvent> events = new ArrayList<>();
    if (legs.length == 0) {
      return events;
    }
    Leg first = legs[0];
    Leg last = legs[legs.length - 1];
    events.add(
        new PlannedEvent(
            HandlingEvent.Type.RECEIVE,
            code(first.getLoadLocation()),
            null,
            first.getLoadTime().minus(ShipmentInsights.RECEIVE_BEFORE_LOAD)));
    for (Leg leg : legs) {
      VoyageNumber voyage = leg.getVoyage().getVoyageNumber();
      events.add(
          new PlannedEvent(
              HandlingEvent.Type.LOAD, code(leg.getLoadLocation()), voyage, leg.getLoadTime()));
      events.add(
          new PlannedEvent(
              HandlingEvent.Type.UNLOAD,
              code(leg.getUnloadLocation()),
              voyage,
              leg.getUnloadTime()));
    }
    events.add(
        new PlannedEvent(
            HandlingEvent.Type.CLAIM,
            code(last.getUnloadLocation()),
            null,
            last.getUnloadTime().plus(ShipmentInsights.CLAIM_AFTER_UNLOAD)));
    return events;
  }

  private void voyage(String number, Stop... stops) {
    Voyage.Builder builder =
        new Voyage.Builder(new VoyageNumber(number), location(stops[0].code()));
    for (int i = 1; i < stops.length; i++) {
      builder.addMovement(
          location(stops[i].code()), at(stops[i - 1].departs()), at(stops[i].arrives()));
    }
    Voyage voyage = builder.build();
    entityManager.persist(voyage);
    voyages.put(number, new VoyagePlan(voyage, List.of(stops)));
  }

  private Leg leg(String voyageNumber, String from, String to) {
    VoyagePlan plan = voyages.get(voyageNumber);
    return new Leg(
        plan.voyage(),
        location(from),
        location(to),
        at(plan.stop(from).departs()),
        at(plan.stop(to).arrives()));
  }

  private Location location(String code) {
    Location location = locations.get(code);
    if (location == null) {
      throw new IllegalArgumentException("Unknown demo location " + code);
    }
    return location;
  }

  private LocalDateTime at(Double hoursFromNow) {
    return now.plusMinutes(Math.round(hoursFromNow * 60));
  }

  private static String code(Location location) {
    return location.getUnLocode().getIdString();
  }

  private static Stop start(String code, double departs) {
    return new Stop(code, null, departs);
  }

  private static Stop call(String code, double arrives, double departs) {
    return new Stop(code, arrives, departs);
  }

  private static Stop end(String code, double arrives) {
    return new Stop(code, arrives, null);
  }

  private record Stop(String code, Double arrives, Double departs) {}

  private record VoyagePlan(Voyage voyage, List<Stop> stops) {

    Stop stop(String code) {
      return stops.stream()
          .filter(stop -> stop.code().equals(code))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException(code + " is not on this voyage"));
    }
  }

  private record PlannedEvent(
      HandlingEvent.Type type, String location, VoyageNumber voyage, LocalDateTime time) {}
}
