package org.eclipse.cargotracker.ai;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.eclipse.cargotracker.ai.AssistantReply.Outcome;

/** Counts assistant outcomes and keeps recent latencies for the dashboard. */
@ApplicationScoped
public class AiStats {

  private static final int WINDOW = 100;

  private final Map<Outcome, Long> counts = new EnumMap<>(Outcome.class);
  private final ArrayDeque<Long> latencies = new ArrayDeque<>();
  private long tokens;
  private long answersWithTokens;
  private String lastModel;
  private Outcome lastOutcome;
  private String lastReason;
  private Long lastAt;

  public synchronized void record(AssistantReply reply) {
    counts.merge(reply.outcome(), 1L, Long::sum);
    lastOutcome = reply.outcome();
    lastReason = reply.reason();
    lastAt = System.currentTimeMillis();
    if (reply.model() != null) {
      lastModel = reply.model();
    }
    if (reply.modelCalls() > 0) {
      latencies.addLast(reply.latencyMillis());
      if (latencies.size() > WINDOW) {
        latencies.removeFirst();
      }
    }
    if (reply.inputTokens() != null || reply.outputTokens() != null) {
      tokens += value(reply.inputTokens()) + value(reply.outputTokens());
      answersWithTokens++;
    }
  }

  public synchronized Snapshot snapshot() {
    long questions = counts.values().stream().mapToLong(Long::longValue).sum();
    List<Long> sorted = new ArrayList<>(latencies);
    Collections.sort(sorted);
    return new Snapshot(
        questions,
        count(Outcome.ANSWERED),
        count(Outcome.REFUSED),
        count(Outcome.CHECK_FAILED),
        count(Outcome.FALLBACK),
        count(Outcome.OFF),
        percentile(sorted, 0.50),
        percentile(sorted, 0.95),
        answersWithTokens == 0 ? null : tokens / answersWithTokens,
        lastModel,
        lastOutcome == null ? null : lastOutcome.name(),
        lastReason,
        lastAt);
  }

  private long count(Outcome outcome) {
    return counts.getOrDefault(outcome, 0L);
  }

  private static Long percentile(List<Long> sorted, double percentile) {
    if (sorted.isEmpty()) {
      return null;
    }
    int index = Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1);
    return sorted.get(index);
  }

  private static long value(Integer number) {
    return number == null ? 0 : number;
  }

  public record Snapshot(
      long questions,
      long answered,
      long refused,
      long checkFailed,
      long fallbacks,
      long off,
      Long p50LatencyMillis,
      Long p95LatencyMillis,
      Long averageTokens,
      String lastModel,
      String lastOutcome,
      String lastReason,
      Long lastAt) {}
}
