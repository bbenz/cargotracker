package org.eclipse.cargotracker.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AiSettingsTest {

  @Test
  void usesTheResponsesApiByDefault() {
    AiSettings settings = AiSettings.fromEnvironment(Map.of());

    assertThat(settings.usesResponsesApi()).isTrue();
    assertThat(settings.chatModel()).isEqualTo("gpt-6.1-sol");
    assertThat(settings.timeoutSeconds()).isEqualTo(20);
    assertThat(settings.providerName()).isEqualTo("openai");
  }

  @Test
  void readsTheSameNamesAsTheContainer() {
    AiSettings settings =
        AiSettings.fromEnvironment(
            Map.of(
                "AI_BASE_URL", "https://example.services.ai.azure.com/openai/v1/",
                "AI_API", "chat-completions",
                "AI_CHAT_MODEL", "gpt-5-mini",
                "AI_REASONING_EFFORT", "low",
                "AI_TIMEOUT_SECONDS", "3"));

    assertThat(settings.usesResponsesApi()).isFalse();
    assertThat(settings.chatModel()).isEqualTo("gpt-5-mini");
    assertThat(settings.reasoningEffort()).isEqualTo("low");
    assertThat(settings.timeoutSeconds()).isEqualTo(3);
    assertThat(settings.providerName()).isEqualTo("azure.ai.openai");
  }
}
