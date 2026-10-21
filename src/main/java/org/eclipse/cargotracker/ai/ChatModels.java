package org.eclipse.cargotracker.ai;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import java.time.Duration;
import java.util.List;

/** Builds the chat model. The application and the evaluation tests both use this class. */
public final class ChatModels {

  private ChatModels() {}

  public static ChatModel create(AiSettings settings, List<ChatModelListener> listeners) {
    Duration timeout = Duration.ofSeconds(settings.timeoutSeconds());
    String effort = settings.reasoningEffort().isBlank() ? null : settings.reasoningEffort();

    ChatModel model;
    if (settings.usesResponsesApi()) {
      model =
          OpenAiResponsesChatModel.builder()
              .httpClientBuilder(
                  new JdkHttpClientBuilder().connectTimeout(timeout).readTimeout(timeout))
              .baseUrl(settings.baseUrl())
              .apiKey(settings.apiKey())
              .modelName(settings.chatModel())
              .reasoningEffort(effort)
              .strictJsonSchema(true)
              .strictTools(true)
              .listeners(listeners)
              .build();
    } else {
      model =
          OpenAiChatModel.builder()
              .baseUrl(settings.baseUrl())
              .apiKey(settings.apiKey())
              .modelName(settings.chatModel())
              .reasoningEffort(effort)
              .timeout(timeout)
              .maxRetries(0)
              .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
              .strictJsonSchema(true)
              .listeners(listeners)
              .build();
    }
    return new RetryOnceChatModel(model);
  }
}
