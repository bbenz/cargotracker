package org.eclipse.cargotracker.ai;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.guardrail.InputGuardrailException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.cargotracker.ai.AssistantReply.Outcome;

@ApplicationScoped
public class CargoQuestionService {

  static final String FALLBACK_MESSAGE =
      "The assistant is not available right now. The rest of the page still works.";
  static final String CHECK_FAILED_MESSAGE =
      "The answer did not pass the checks, so the page does not show it.";

  @Inject private CargoAssistant assistant;
  @Inject private CargoLookup cargos;
  @Inject private DocumentSourceCatalog sources;
  @Inject private Validator validator;
  @Inject private Logger log;

  public CargoQuestionService() {}

  CargoQuestionService(
      CargoAssistant assistant,
      CargoLookup cargos,
      DocumentSourceCatalog sources,
      Validator validator,
      Logger log) {
    this.assistant = assistant;
    this.cargos = cargos;
    this.sources = sources;
    this.validator = validator;
    this.log = log;
  }

  public CargoAnswer ask(String question) {
    return answer(question).result();
  }

  public AssistantReply answer(String question) {
    long started = System.nanoTime();
    ModelUsage.Totals usage = ModelUsage.start();
    try {
      CargoAnswer reply = assistant.answer(question);
      String problem = checkProblem(reply);
      if (problem == null) {
        return reply(Outcome.ANSWERED, reply, null, null, started, usage);
      }
      log.info("Assistant answer failed the checks: " + problem);
      return reply(
          Outcome.CHECK_FAILED,
          CargoAnswer.cannotAnswer(),
          CHECK_FAILED_MESSAGE,
          problem,
          started,
          usage);
    } catch (InputGuardrailException e) {
      return reply(
          Outcome.REFUSED,
          CargoAnswer.cannotAnswer(),
          guardMessage(e),
          "input guard",
          started,
          usage);
    } catch (RuntimeException e) {
      // timeout, bad key, or model down
      log.log(Level.WARNING, "Assistant unavailable", e);
      return reply(
          Outcome.FALLBACK,
          CargoAnswer.cannotAnswer(),
          FALLBACK_MESSAGE,
          reason(e),
          started,
          usage);
    } finally {
      ModelUsage.stop();
    }
  }

  private String checkProblem(CargoAnswer reply) {
    if (!validator.validate(reply).isEmpty()) {
      return "The answer does not fit the CargoAnswer record.";
    }
    for (String trackingId : reply.trackingIds()) {
      if (!cargos.exists(trackingId)) {
        return "Unknown tracking ID: " + trackingId;
      }
    }
    for (String source : reply.sources()) {
      if (!sources.isKnown(source)) {
        return "Unknown source: " + source;
      }
    }
    return null;
  }

  private AssistantReply reply(
      Outcome outcome,
      CargoAnswer answer,
      String message,
      String reason,
      long started,
      ModelUsage.Totals usage) {
    long latencyMillis = (System.nanoTime() - started) / 1_000_000;
    return new AssistantReply(
        outcome,
        answer,
        message,
        reason,
        usage.model(),
        latencyMillis,
        usage.calls(),
        usage.inputTokens(),
        usage.outputTokens(),
        null);
  }

  static String guardMessage(InputGuardrailException e) {
    String message = e.getMessage() == null ? "" : e.getMessage();
    int start = message.lastIndexOf("message: ");
    String text = start < 0 ? message : message.substring(start + "message: ".length());
    return text.isBlank() ? "Ask about cargo, shipping, routes, documents, or tracking IDs." : text;
  }

  static String reason(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof TimeoutException || cause instanceof HttpTimeoutException) {
        return "The model did not answer in time.";
      }
      if (cause instanceof AuthenticationException) {
        return "The model endpoint rejected the API key.";
      }
      if (cause instanceof ModelNotFoundException) {
        return "The model endpoint does not know this model name.";
      }
      if (cause instanceof HttpException http) {
        return httpReason(http.statusCode());
      }
      if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
        return "The model endpoint could not be reached.";
      }
    }
    return error.getClass().getSimpleName() + ": " + error.getMessage();
  }

  private static String httpReason(int status) {
    if (status == 401 || status == 403) {
      return "The model endpoint rejected the API key.";
    }
    if (status == 404) {
      return "The model endpoint does not know this model name.";
    }
    if (status == 429) {
      return "The model endpoint is limiting requests.";
    }
    if (status >= 500) {
      return "The model endpoint had a server error.";
    }
    return "The model endpoint returned HTTP " + status + ".";
  }
}
