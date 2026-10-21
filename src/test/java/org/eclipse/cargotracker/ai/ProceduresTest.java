package org.eclipse.cargotracker.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProceduresTest {

  private final Procedures procedures = new Procedures(Path.of("src/main/ai-docs"));

  @Test
  void misdirectedCargoPointsToTheMisdirectedProcedure() {
    assertThat(procedures.forIssue("MISDIRECTED"))
        .hasValueSatisfying(
            text ->
                assertThat(text)
                    .startsWith("Procedure (misdirected-cargo.md): Misdirected cargo has moved"));
  }

  @Test
  void lateCargoPointsToTheDelayProcedure() {
    assertThat(procedures.forIssue("LATE"))
        .hasValueSatisfying(text -> assertThat(text).contains("delivery-delays.md"));
  }

  @Test
  void anIssueWithoutAProcedureAddsNothing() {
    assertThat(procedures.forIssue("NOT_ROUTED")).isEmpty();
    assertThat(procedures.forIssue(null)).isEmpty();
  }

  @Test
  void everyProcedureIsAnIndexedDocument() {
    assertThat(Procedures.BY_ISSUE.values())
        .allSatisfy(source -> assertThat(Path.of("src/main/ai-docs", source)).exists());
  }
}
