package org.eclipse.cargotracker.ai;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The application, not the model, decides which procedure applies to a problem. The tools pass that
 * procedure to the model, so answers cite the right document.
 */
@ApplicationScoped
public class Procedures {

  static final Map<String, String> BY_ISSUE =
      Map.of(
          "MISDIRECTED", "misdirected-cargo.md",
          "MISROUTED", "rerouting.md",
          "LATE", "delivery-delays.md");

  @Inject private AiConfiguration configuration;

  private Path docsDir;
  private final Map<String, String> texts = new ConcurrentHashMap<>();

  public Procedures() {}

  Procedures(Path docsDir) {
    this.docsDir = docsDir;
  }

  /** Returns "Procedure (file.md): text", or nothing when no document applies. */
  public Optional<String> forIssue(String issue) {
    String source = issue == null ? null : BY_ISSUE.get(issue);
    if (source == null) {
      return Optional.empty();
    }
    String text = texts.computeIfAbsent(source, this::read);
    return text.isEmpty() ? Optional.empty() : Optional.of("Procedure (" + source + "): " + text);
  }

  private String read(String source) {
    Path file = (docsDir != null ? docsDir : configuration.docsDir()).resolve(source);
    try {
      return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
          .skip(1)
          .map(String::trim)
          .filter(line -> !line.isEmpty())
          .collect(Collectors.joining(" "));
    } catch (IOException e) {
      return "";
    }
  }
}
