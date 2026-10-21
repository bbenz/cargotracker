package org.eclipse.cargotracker.ai;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/** Read-only tools. The assistant can look at cargo, but it cannot book or change anything. */
@ApplicationScoped
public class CargoTools {

  @Inject private CargoLookup cargoLookup;
  @Inject private Tracer tracer;

  public CargoTools() {}

  public CargoTools(CargoLookup cargoLookup) {
    this(cargoLookup, OpenTelemetry.noop().getTracer("test"));
  }

  CargoTools(CargoLookup cargoLookup, Tracer tracer) {
    this.cargoLookup = cargoLookup;
    this.tracer = tracer;
  }

  @Tool(
      "Return the status of one cargo: route, last known location, next expected activity, ETA,"
          + " arrival deadline, misdirected flag, and any problem that needs attention")
  public CargoStatusDetails cargoStatus(@P("Tracking ID, for example ABC123") String trackingId) {
    Span span = toolSpan("cargoStatus");
    try {
      return cargoLookup.cargoStatus(trackingId).orElse(null);
    } finally {
      span.end();
    }
  }

  @Tool("List all cargo with tracking ID, origin, destination, and status")
  public List<CargoSummary> listCargos() {
    Span span = toolSpan("listCargos");
    try {
      return cargoLookup.listCargos();
    } finally {
      span.end();
    }
  }

  @Tool(
      "List cargo that needs attention now: misdirected, misrouted, not routed, or late."
          + " Each item has the tracking ID, the issue, and details, including port notices")
  public List<AttentionItem> shipmentsNeedingAttention() {
    Span span = toolSpan("shipmentsNeedingAttention");
    try {
      return cargoLookup.needingAttention();
    } finally {
      span.end();
    }
  }

  @Tool(
      "List cargo that is at a location now, or that will load or unload there."
          + " The location can be a city name, such as Rotterdam, or a UN/LOCODE, such as NLRTM")
  public List<CargoSummary> shipmentsAtLocation(@P("City name or UN/LOCODE") String location) {
    Span span = toolSpan("shipmentsAtLocation");
    try {
      return cargoLookup.atLocation(location);
    } finally {
      span.end();
    }
  }

  private Span toolSpan(String toolName) {
    Span span = tracer.spanBuilder("execute_tool " + toolName).startSpan();
    span.setAttribute("gen_ai.operation.name", "execute_tool");
    span.setAttribute("gen_ai.tool.name", toolName);
    return span;
  }
}
