package org.eclipse.cargotracker.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.jupiter.api.Test;

class RetryOnceChatModelTest {

  @Test
  void retriesOnceAfterARateLimit() {
    ScriptedModel model = new ScriptedModel(new RateLimitException("busy"), null);

    ChatResponse response = new RetryOnceChatModel(model).chat(request());

    assertThat(response.aiMessage().text()).isEqualTo("ok");
    assertThat(model.calls).isEqualTo(2);
  }

  @Test
  void retriesOnceAfterAServerError() {
    ScriptedModel model = new ScriptedModel(new HttpException(503, "unavailable"), null);

    new RetryOnceChatModel(model).chat(request());

    assertThat(model.calls).isEqualTo(2);
  }

  @Test
  void doesNotRetryATimeout() {
    ScriptedModel model = new ScriptedModel(new TimeoutException("slow"), null);

    assertThatThrownBy(() -> new RetryOnceChatModel(model).chat(request()))
        .isInstanceOf(TimeoutException.class);
    assertThat(model.calls).isEqualTo(1);
  }

  @Test
  void doesNotRetryABadKey() {
    ScriptedModel model = new ScriptedModel(new AuthenticationException("bad key"), null);

    assertThatThrownBy(() -> new RetryOnceChatModel(model).chat(request()))
        .isInstanceOf(AuthenticationException.class);
    assertThat(model.calls).isEqualTo(1);
  }

  @Test
  void givesUpAfterTheSecondFailure() {
    ScriptedModel model =
        new ScriptedModel(new RateLimitException("busy"), new RateLimitException("still busy"));

    assertThatThrownBy(() -> new RetryOnceChatModel(model).chat(request()))
        .isInstanceOf(RateLimitException.class);
    assertThat(model.calls).isEqualTo(2);
  }

  private static ChatRequest request() {
    return ChatRequest.builder().messages(UserMessage.from("Where is ABC123?")).build();
  }

  /** Throws the given errors in order, then answers "ok". A null entry means "answer". */
  private static final class ScriptedModel implements ChatModel {

    private final Deque<RuntimeException> errors = new ArrayDeque<>();
    private int calls;

    ScriptedModel(RuntimeException first, RuntimeException second) {
      if (first != null) {
        errors.add(first);
      }
      if (second != null) {
        errors.add(second);
      }
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
      calls++;
      RuntimeException error = errors.poll();
      if (error != null) {
        throw error;
      }
      return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
    }
  }
}
