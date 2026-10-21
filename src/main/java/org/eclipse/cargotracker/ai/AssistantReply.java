package org.eclipse.cargotracker.ai;

/**
 * What the REST API returns for a question: the typed CargoAnswer from the model, plus the details
 * the dashboard shows about the call.
 */
public record AssistantReply(
    Outcome outcome,
    CargoAnswer result,
    String message,
    String reason,
    String model,
    long latencyMillis,
    int modelCalls,
    Integer inputTokens,
    Integer outputTokens,
    String traceId) {

  public enum Outcome {
    /** The answer passed every check. */
    ANSWERED,
    /** The input guard stopped the question before it reached the model. */
    REFUSED,
    /** The model answered, but the answer failed the output checks. */
    CHECK_FAILED,
    /** The model failed, timed out, or rejected the call. */
    FALLBACK,
    /** The AI feature is turned off. */
    OFF
  }

  static AssistantReply off(String message) {
    return new AssistantReply(
        Outcome.OFF, CargoAnswer.cannotAnswer(), message, null, null, 0, 0, null, null, null);
  }

  AssistantReply withTraceId(String id) {
    return new AssistantReply(
        outcome,
        result,
        message,
        reason,
        model,
        latencyMillis,
        modelCalls,
        inputTokens,
        outputTokens,
        id);
  }
}
