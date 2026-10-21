package org.eclipse.cargotracker.ai;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RetriableException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.Set;

/**
 * Retries a model call once after a rate limit or a server error. A timeout is not retried, so a
 * user never waits for two timeouts in a row.
 */
class RetryOnceChatModel implements ChatModel {

  private final ChatModel delegate;

  RetryOnceChatModel(ChatModel delegate) {
    this.delegate = delegate;
  }

  @Override
  public ChatResponse doChat(ChatRequest chatRequest) {
    try {
      return delegate.chat(chatRequest);
    } catch (RuntimeException e) {
      if (!isTemporary(e)) {
        throw e;
      }
      return delegate.chat(chatRequest);
    }
  }

  static boolean isTemporary(Throwable error) {
    if (error instanceof TimeoutException) {
      return false;
    }
    if (error instanceof RetriableException) {
      return true;
    }
    if (error instanceof HttpException http) {
      return http.statusCode() == 429 || http.statusCode() >= 500;
    }
    return false;
  }

  @Override
  public ChatRequestParameters defaultRequestParameters() {
    return delegate.defaultRequestParameters();
  }

  @Override
  public Set<Capability> supportedCapabilities() {
    return delegate.supportedCapabilities();
  }

  @Override
  public ModelProvider provider() {
    return delegate.provider();
  }
}
