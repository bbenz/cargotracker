package org.eclipse.cargotracker.ai;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Adds up model calls and tokens for one question. The assistant calls the model on the request
 * thread, so a thread-local total is enough.
 */
public class ModelUsage implements ChatModelListener {

  private static final ThreadLocal<Totals> CURRENT = new ThreadLocal<>();

  static Totals start() {
    Totals totals = new Totals();
    CURRENT.set(totals);
    return totals;
  }

  static void stop() {
    CURRENT.remove();
  }

  @Override
  public void onResponse(ChatModelResponseContext context) {
    Totals totals = CURRENT.get();
    if (totals != null) {
      totals.add(context.chatResponse().modelName(), context.chatResponse().tokenUsage());
    }
  }

  @Override
  public void onError(ChatModelErrorContext context) {
    Totals totals = CURRENT.get();
    if (totals != null) {
      totals.calls++;
    }
  }

  static final class Totals {

    private String model;
    private int calls;
    private Integer inputTokens;
    private Integer outputTokens;

    void add(String responseModel, TokenUsage usage) {
      calls++;
      if (responseModel != null) {
        model = responseModel;
      }
      if (usage != null) {
        inputTokens = sum(inputTokens, usage.inputTokenCount());
        outputTokens = sum(outputTokens, usage.outputTokenCount());
      }
    }

    String model() {
      return model;
    }

    int calls() {
      return calls;
    }

    Integer inputTokens() {
      return inputTokens;
    }

    Integer outputTokens() {
      return outputTokens;
    }

    private static Integer sum(Integer total, Integer value) {
      if (value == null) {
        return total;
      }
      return total == null ? value : total + value;
    }
  }
}
