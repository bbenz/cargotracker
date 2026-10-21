package org.eclipse.cargotracker.interfaces.dashboard;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Port and weather notices are plain Markdown files in the document folder. Their file names start
 * with "notice-" and their title names the UN/LOCODE, for example "(NLRTM)".
 */
@ApplicationScoped
public class PortNotices {

  private static final Pattern CODE = Pattern.compile("\\(([A-Z]{2}[A-Z2-9]{3})\\)");

  private volatile List<Notice> notices;

  public List<Notice> all() {
    List<Notice> loaded = notices;
    if (loaded == null) {
      loaded = load(docsDir());
      notices = loaded;
    }
    return loaded;
  }

  public Optional<Notice> forPort(String unLocode) {
    return all().stream().filter(notice -> notice.port().equals(unLocode)).findFirst();
  }

  static List<Notice> load(Path dir) {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .filter(file -> file.getFileName().toString().startsWith("notice-"))
          .filter(file -> file.getFileName().toString().endsWith(".md"))
          .sorted()
          .map(PortNotices::read)
          .flatMap(Optional::stream)
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Optional<Notice> read(Path file) {
    try {
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      if (lines.isEmpty()) {
        return Optional.empty();
      }
      String title =
          lines.get(0).replaceFirst("^#\\s*", "").replaceFirst("^Sample content:\\s*", "");
      Matcher code = CODE.matcher(title);
      if (!code.find()) {
        return Optional.empty();
      }
      String summary =
          lines.stream()
              .skip(1)
              .map(String::trim)
              .filter(line -> !line.isEmpty())
              .findFirst()
              .orElse("");
      return Optional.of(new Notice(code.group(1), title, summary, file.getFileName().toString()));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private static Path docsDir() {
    return Path.of(
        ConfigProvider.getConfig()
            .getOptionalValue("ai.docs.dir", String.class)
            .filter(value -> !value.isBlank())
            .orElse("src/main/ai-docs"));
  }

  public record Notice(String port, String title, String summary, String source) {}
}
