package org.eclipse.cargotracker.ai;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class DocumentSourceCatalog {

  static final Set<String> TOOL_SOURCES =
      Set.of(
          "tool:cargoStatus",
          "tool:listCargos",
          "tool:shipmentsNeedingAttention",
          "tool:shipmentsAtLocation");

  private final Set<String> sources = ConcurrentHashMap.newKeySet();

  public void add(String source) {
    if (source != null && !source.isBlank()) {
      sources.add(source);
    }
  }

  public boolean isKnown(String source) {
    return source != null && (TOOL_SOURCES.contains(source) || sources.contains(source));
  }

  public boolean isDocument(String source) {
    return source != null && sources.contains(source);
  }

  public Set<String> all() {
    return Set.copyOf(sources);
  }
}
