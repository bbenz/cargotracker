package org.eclipse.cargotracker.ai;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Path("assistant")
@Produces(MediaType.APPLICATION_JSON)
public class AssistantResource {

  @Inject private CargoQuestionService questionService;
  @Inject private AiConfiguration configuration;
  @Inject private AiStats stats;
  @Inject private DocumentSourceCatalog sources;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public AssistantReply ask(@Valid AssistantQuestion question) {
    AssistantReply reply =
        configuration.configured()
            ? questionService.answer(question.question())
            : AssistantReply.off(offMessage());
    Span span = Span.current();
    span.setAttribute("cargo.assistant.outcome", reply.outcome().name());
    if (!reply.result().trackingIds().isEmpty()) {
      span.setAttribute("cargo.tracking_id", reply.result().trackingIds().get(0));
    }
    stats.record(reply);
    return reply.withTraceId(traceId(span));
  }

  @GET
  @Path("status")
  public AiStatus status() {
    return new AiStatus(
        configuration.enabled(),
        configuration.configured(),
        configuration.api(),
        configuration.chatModel(),
        configuration.reasoningEffort(),
        configuration.timeoutSeconds(),
        configuration.settings().providerName(),
        stats.snapshot());
  }

  /** Returns the text of a cited document, so the page can show where an answer came from. */
  @GET
  @Path("sources/{name}")
  @Produces(MediaType.TEXT_PLAIN)
  public Response source(@PathParam("name") String name) {
    if (!sources.isDocument(name)) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    java.nio.file.Path docsDir = configuration.docsDir().toAbsolutePath().normalize();
    java.nio.file.Path file = docsDir.resolve(name).normalize();
    if (!file.startsWith(docsDir) || !Files.isRegularFile(file)) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    try {
      return Response.ok(Files.readString(file, StandardCharsets.UTF_8)).build();
    } catch (IOException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  private String offMessage() {
    return configuration.enabled()
        ? "The assistant has no model endpoint or key. The rest of the page still works."
        : "The assistant is turned off. The rest of the page still works.";
  }

  private static String traceId(Span span) {
    SpanContext context = span.getSpanContext();
    return context.isValid() ? context.getTraceId() : null;
  }

  public record AssistantQuestion(@NotBlank @Size(max = 2000) String question) {}

  public record AiStatus(
      boolean enabled,
      boolean configured,
      String api,
      String model,
      String reasoningEffort,
      int timeoutSeconds,
      String provider,
      AiStats.Snapshot stats) {}
}
