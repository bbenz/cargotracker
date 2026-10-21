package org.eclipse.cargotracker.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the guard inside a real AI service, with document search that returns long passages. */
class GuardWithDocumentSearchTest {

  private static final String LONG_PASSAGE = "Misdirected cargo needs a new route. ".repeat(40);

  private final ContentRetriever longDocuments =
      query -> List.of(Content.from(TextSegment.from(LONG_PASSAGE)));

  @Test
  void aShortQuestionPassesEvenWhenTheDocumentsAreLong() {
    CargoAssistant assistant =
        AssistantFactory.create(
            new AnsweringChatModel(), longDocuments, new CargoTools(new FakeCargoLookup()));

    CargoAnswer answer = assistant.answer("Which shipments need attention, and why?");

    assertThat(answer.answer()).isEqualTo("Two shipments need attention.");
  }

  @Test
  void anOffTopicQuestionIsStillRefused() {
    CargoAssistant assistant =
        AssistantFactory.create(
            new ThrowingChatModel(), longDocuments, new CargoTools(new FakeCargoLookup()));

    assertThatThrownBy(() -> assistant.answer("Write a poem about cats"))
        .isInstanceOf(InputGuardrailException.class);
  }

  private static final class AnsweringChatModel implements ChatModel {

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
      return ChatResponse.builder()
          .aiMessage(
              AiMessage.from(
                  "{\"answer\":\"Two shipments need attention.\",\"trackingIds\":[],"
                      + "\"sources\":[\"tool:shipmentsNeedingAttention\"]}"))
          .build();
    }
  }
}
