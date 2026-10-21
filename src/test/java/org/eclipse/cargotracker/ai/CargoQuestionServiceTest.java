package org.eclipse.cargotracker.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.guardrail.InputGuardrailException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.logging.Logger;
import org.eclipse.cargotracker.ai.AssistantReply.Outcome;
import org.junit.jupiter.api.Test;

class CargoQuestionServiceTest {

  private final FakeCargoLookup cargoLookup = new FakeCargoLookup();
  private final DocumentSourceCatalog sourceCatalog = new DocumentSourceCatalog();
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  private final Logger logger = Logger.getLogger(getClass().getName());

  @Test
  void returnsFallbackWhenAssistantThrows() {
    CargoQuestionService service =
        service(
            question -> {
              throw new IllegalStateException("down");
            });

    AssistantReply reply = service.answer("Where is ABC123?");

    assertThat(reply.outcome()).isEqualTo(Outcome.FALLBACK);
    assertThat(reply.result()).isEqualTo(CargoAnswer.cannotAnswer());
    assertThat(service.ask("Where is ABC123?")).isEqualTo(CargoAnswer.cannotAnswer());
  }

  @Test
  void explainsATimeout() {
    CargoQuestionService service =
        service(
            question -> {
              throw new TimeoutException(new java.net.http.HttpTimeoutException("slow"));
            });

    AssistantReply reply = service.answer("Where is ABC123?");

    assertThat(reply.outcome()).isEqualTo(Outcome.FALLBACK);
    assertThat(reply.reason()).isEqualTo("The model did not answer in time.");
  }

  @Test
  void reportsARefusedQuestion() {
    CargoQuestionService service =
        service(
            question -> {
              throw new InputGuardrailException(
                  "The guardrail StaysOnTopic failed with this message: Ask about cargo.");
            });

    AssistantReply reply = service.answer("Tell me a joke");

    assertThat(reply.outcome()).isEqualTo(Outcome.REFUSED);
    assertThat(reply.message()).isEqualTo("Ask about cargo.");
  }

  @Test
  void returnsFallbackForInvalidRecord() {
    CargoQuestionService service =
        service(question -> new CargoAnswer("", List.of("ABC123"), List.of("tool:cargoStatus")));

    AssistantReply reply = service.answer("Where is ABC123?");

    assertThat(reply.outcome()).isEqualTo(Outcome.CHECK_FAILED);
    assertThat(reply.result()).isEqualTo(CargoAnswer.cannotAnswer());
  }

  @Test
  void returnsFallbackForUnknownTrackingId() {
    CargoQuestionService service =
        service(
            question ->
                new CargoAnswer("Found it", List.of("ZZZ999"), List.of("tool:cargoStatus")));

    AssistantReply reply = service.answer("Where is ZZZ999?");

    assertThat(reply.outcome()).isEqualTo(Outcome.CHECK_FAILED);
    assertThat(reply.reason()).contains("ZZZ999");
  }

  @Test
  void returnsFallbackForUnknownSource() {
    CargoQuestionService service =
        service(
            question ->
                new CargoAnswer("ABC123 is in New York.", List.of("ABC123"), List.of("wiki")));

    assertThat(service.answer("Where is ABC123?").outcome()).isEqualTo(Outcome.CHECK_FAILED);
  }

  @Test
  void acceptsKnownTrackingIdAndSource() {
    sourceCatalog.add("customs-holds.md");
    CargoQuestionService service =
        service(
            question ->
                new CargoAnswer(
                    "ABC123 is in New York.", List.of("ABC123"), List.of("customs-holds.md")));

    AssistantReply reply = service.answer("Where is ABC123?");

    assertThat(reply.outcome()).isEqualTo(Outcome.ANSWERED);
    assertThat(reply.result().answer()).contains("ABC123");
  }

  @Test
  void acceptsTheNewToolSources() {
    CargoQuestionService service =
        service(
            question ->
                new CargoAnswer(
                    "JKL567 needs attention.",
                    List.of("JKL567"),
                    List.of("tool:shipmentsNeedingAttention", "tool:shipmentsAtLocation")));

    assertThat(service.answer("Which cargo needs attention?").outcome())
        .isEqualTo(Outcome.ANSWERED);
  }

  private CargoQuestionService service(CargoAssistant assistant) {
    return new CargoQuestionService(assistant, cargoLookup, sourceCatalog, validator, logger);
  }
}
