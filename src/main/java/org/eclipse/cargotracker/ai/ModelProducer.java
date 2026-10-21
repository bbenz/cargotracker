package org.eclipse.cargotracker.ai;

import dev.langchain4j.model.chat.ChatModel;
import io.opentelemetry.api.trace.Tracer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ModelProducer {

  @Inject private AiConfiguration configuration;
  @Inject private Tracer tracer;

  @Produces
  @ApplicationScoped
  ChatModel chatModel() {
    if (!configuration.configured()) {
      return new FallbackChatModel();
    }
    AiSettings settings = configuration.settings();
    return ChatModels.create(
        settings, List.of(new ModelCallTracing(tracer, settings.providerName()), new ModelUsage()));
  }
}
