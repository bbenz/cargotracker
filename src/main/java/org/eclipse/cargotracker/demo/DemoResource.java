package org.eclipse.cargotracker.demo;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Starts, stops, and steps the demo traffic. This is a demo control, not a production API. */
@Path("demo/traffic")
@Produces(MediaType.APPLICATION_JSON)
public class DemoResource {

  @Inject private DemoTraffic traffic;

  @GET
  public TrafficStatus status() {
    return status(null);
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  public TrafficStatus update(TrafficUpdate update) {
    traffic.setEnabled(update != null && update.enabled());
    return status(null);
  }

  @POST
  @Path("step")
  public TrafficStatus step() {
    return status(traffic.step().orElse("No planned event in the next 72 hours."));
  }

  private TrafficStatus status(String reported) {
    return new TrafficStatus(
        traffic.isEnabled(),
        traffic.getIntervalSeconds(),
        reported == null ? traffic.getLastReport() : reported);
  }

  public record TrafficUpdate(boolean enabled) {}

  public record TrafficStatus(boolean enabled, int intervalSeconds, String lastReport) {}
}
