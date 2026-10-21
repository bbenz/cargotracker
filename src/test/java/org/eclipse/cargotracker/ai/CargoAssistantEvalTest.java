package org.eclipse.cargotracker.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import io.opentelemetry.api.OpenTelemetry;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CargoAssistantEvalTest {

  // Defaults match the evaluation table in the talk. Set EVAL_MAX_TOKENS or EVAL_P95_MILLIS to
  // adjust the gate for another model. Set EVAL_LIMIT to run fewer questions in a live demo.
  private static final int MAX_TOKENS_PER_ANSWER = limit("EVAL_MAX_TOKENS", 2_500);
  private static final long P95_LATENCY_LIMIT_MILLIS = limit("EVAL_P95_MILLIS", 4_000);
  private static final int QUESTION_LIMIT = limit("EVAL_LIMIT", Integer.MAX_VALUE);

  /** 38 of 40 in the full run. */
  private static final double REQUIRED_SHARE_CORRECT = 0.95;

  private CargoAssistant guardedAssistant;
  private DocumentSourceCatalog sourceCatalog;
  private List<EvalResult> modelResults = List.of();
  private boolean modelConfigured;

  @BeforeAll
  void runModelEvalOnce() {
    modelConfigured = isModelConfigured();
    if (!modelConfigured) {
      System.out.println("AI model not configured; skipping model-backed evals.");
      return;
    }

    sourceCatalog = new DocumentSourceCatalog();
    EvalTokenCounter tokenCounter = new EvalTokenCounter();
    CargoAssistant modelAssistant = configuredAssistant(tokenCounter);
    List<EvalResult> results = new ArrayList<>();
    for (EvalCase evalCase :
        EvalCase.load("/eval/cargo-questions.csv").stream().limit(QUESTION_LIMIT).toList()) {
      tokenCounter.reset();
      long started = System.nanoTime();
      CargoAnswer answer = CargoAnswer.cannotAnswer();
      String error = null;
      try {
        answer = modelAssistant.answer(evalCase.question());
      } catch (RuntimeException e) {
        error = e.getMessage();
      }
      long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
      results.add(
          new EvalResult(evalCase, answer, elapsedMillis, tokenCounter.totalTokens(), error));
    }
    modelResults = List.copyOf(results);
  }

  @BeforeEach
  void setUp() {
    guardedAssistant =
        AssistantFactory.create(
            new ThrowingChatModel(), query -> List.of(), new CargoTools(new FakeCargoLookup()));
  }

  @Test
  void findsTheRightCargo() {
    assumeModelConfigured();

    long correct = modelResults.stream().filter(EvalResult::foundExpectedId).count();
    long required = (long) Math.ceil(modelResults.size() * REQUIRED_SHARE_CORRECT);

    assertThat(correct).isGreaterThanOrEqualTo(required);
  }

  @ParameterizedTest
  @CsvFileSource(resources = "/eval/off-topic.csv")
  void refusesOffTopicQuestions(String question) {
    assertThatThrownBy(() -> guardedAssistant.answer(question))
        .isInstanceOf(InputGuardrailException.class);
  }

  @Test
  void citesRealSources() {
    assumeModelConfigured();

    assertThat(modelResults)
        .allSatisfy(
            result -> {
              assertThat(result.answer().sources()).isNotEmpty();
              assertThat(result.answer().sources()).allMatch(sourceCatalog::isKnown);
            });
  }

  @Test
  void returnsValidData() {
    assumeModelConfigured();
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    assertThat(modelResults)
        .allSatisfy(result -> assertThat(validator.validate(result.answer())).isEmpty());
  }

  @Test
  void answersInTime() {
    assumeModelConfigured();

    long p95LatencyMillis =
        modelResults.stream()
            .map(EvalResult::latencyMillis)
            .sorted()
            .toList()
            .get(percentileIndex(modelResults.size(), 0.95));

    assertThat(p95LatencyMillis).isLessThan(P95_LATENCY_LIMIT_MILLIS);
  }

  @Test
  void costsWhatWeExpect() {
    assumeModelConfigured();

    assertThat(modelResults)
        .allSatisfy(result -> assertThat(result.tokens()).isLessThan(MAX_TOKENS_PER_ANSWER));
  }

  private void assumeModelConfigured() {
    assumeTrue(modelConfigured, "AI model not configured");
  }

  private boolean isModelConfigured() {
    String apiKey = System.getenv("AI_API_KEY");
    return apiKey != null && !apiKey.isBlank();
  }

  private static int limit(String name, int defaultValue) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value.trim());
  }

  private int percentileIndex(int size, double percentile) {
    return Math.max(0, (int) Math.ceil(size * percentile) - 1);
  }

  private CargoAssistant configuredAssistant(EvalTokenCounter tokenCounter) {
    // The same factory and setting names as the application.
    ChatModel chatModel =
        ChatModels.create(AiSettings.fromEnvironment(System.getenv()), List.of(tokenCounter));
    ContentRetriever docs =
        DocumentIndex.build(
            Path.of("src/main/ai-docs"), sourceCatalog, OpenTelemetry.noop().getTracer("eval"));
    return AssistantFactory.create(chatModel, docs, new CargoTools(new FakeCargoLookup()));
  }

  private record EvalResult(
      EvalCase evalCase, CargoAnswer answer, long latencyMillis, int tokens, String error) {

    boolean foundExpectedId() {
      return answer.trackingIds().contains(evalCase.expectedId());
    }
  }

  private static final class EvalTokenCounter implements ChatModelListener {

    private int totalTokens;

    void reset() {
      totalTokens = 0;
    }

    int totalTokens() {
      return totalTokens;
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
      TokenUsage tokenUsage = context.chatResponse().tokenUsage();
      if (tokenUsage == null) {
        return;
      }
      totalTokens += value(tokenUsage.inputTokenCount());
      totalTokens += value(tokenUsage.outputTokenCount());
    }

    private int value(Integer tokenCount) {
      return tokenCount == null ? 0 : tokenCount;
    }
  }
}
