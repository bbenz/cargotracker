package org.eclipse.cargotracker.ai;

import java.util.Locale;
import java.util.Map;

/**
 * Model settings. The application reads them from MicroProfile Config. The evaluation tests read
 * the same names from environment variables, so both use the same model setup.
 */
public record AiSettings(
    String baseUrl,
    String apiKey,
    String chatModel,
    String api,
    String reasoningEffort,
    int timeoutSeconds) {

  public static final String RESPONSES = "responses";
  public static final String CHAT_COMPLETIONS = "chat-completions";

  public static AiSettings fromEnvironment(Map<String, String> env) {
    return new AiSettings(
        value(env, "AI_BASE_URL", "https://api.openai.com/v1"),
        value(env, "AI_API_KEY", ""),
        value(env, "AI_CHAT_MODEL", "gpt-6.1-sol"),
        value(env, "AI_API", RESPONSES),
        value(env, "AI_REASONING_EFFORT", ""),
        Integer.parseInt(value(env, "AI_TIMEOUT_SECONDS", "20")));
  }

  public boolean usesResponsesApi() {
    return !CHAT_COMPLETIONS.equalsIgnoreCase(api);
  }

  public String providerName() {
    return baseUrl.toLowerCase(Locale.ROOT).contains(".azure.com") ? "azure.ai.openai" : "openai";
  }

  private static String value(Map<String, String> env, String name, String defaultValue) {
    String value = env.get(name);
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }
}
