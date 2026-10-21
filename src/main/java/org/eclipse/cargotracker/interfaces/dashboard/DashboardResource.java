package org.eclipse.cargotracker.interfaces.dashboard;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.LocationRepository;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.eclipse.cargotracker.domain.model.voyage.VoyageRepository;
import org.eclipse.cargotracker.interfaces.dashboard.DashboardSnapshot.Movement;
import org.eclipse.cargotracker.interfaces.dashboard.DashboardSnapshot.PortView;
import org.eclipse.cargotracker.interfaces.dashboard.DashboardSnapshot.VoyageView;
import org.eclipse.microprofile.config.ConfigProvider;

/** Read-only data for the shipment dashboard. It uses the existing repositories. */
@Path("dashboard")
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {

  @Inject private ShipmentInsights insights;
  @Inject private LocationRepository locationRepository;
  @Inject private VoyageRepository voyageRepository;
  @Inject private PortNotices notices;

  @GET
  public DashboardSnapshot snapshot() {
    List<PortView> ports =
        locationRepository.findAll().stream()
            .map(DashboardResource::port)
            .flatMap(Optional::stream)
            .sorted(Comparator.comparing(PortView::name))
            .toList();
    List<VoyageView> voyages =
        voyageRepository.findAll().stream()
            .map(DashboardResource::voyage)
            .sorted(Comparator.comparing(VoyageView::number))
            .toList();
    return new DashboardSnapshot(
        System.currentTimeMillis(),
        ports,
        voyages,
        insights.shipments(),
        notices.all(),
        traceUrl());
  }

  private static Optional<PortView> port(Location location) {
    String code = location.getUnLocode().getIdString();
    return PortCoordinates.find(code)
        .map(
            position ->
                new PortView(
                    code,
                    location.getName(),
                    position.latitude(),
                    position.longitude(),
                    position.inland()));
  }

  private static VoyageView voyage(Voyage voyage) {
    List<Movement> movements =
        voyage.getSchedule().getCarrierMovements().stream()
            .map(
                movement ->
                    new Movement(
                        ShipmentInsights.code(movement.getDepartureLocation()),
                        ShipmentInsights.code(movement.getArrivalLocation()),
                        ShipmentInsights.epoch(movement.getDepartureTime()),
                        ShipmentInsights.epoch(movement.getArrivalTime())))
            .toList();
    return new VoyageView(ShipmentInsights.voyageNumber(voyage), movements);
  }

  private static String traceUrl() {
    return ConfigProvider.getConfig()
        .getOptionalValue("dashboard.trace-url", String.class)
        .filter(value -> !value.isBlank())
        .orElse(null);
  }
}
