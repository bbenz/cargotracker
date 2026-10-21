package org.eclipse.cargotracker.interfaces.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PortNoticesTest {

  @Test
  void readsNoticesThatNameAPort(@TempDir Path dir) throws IOException {
    Files.writeString(
        dir.resolve("notice-rotterdam-berths.md"),
        "# Sample content: Port notice, Rotterdam (NLRTM)\n\nBerths are full.\n\nMore text.\n");
    Files.writeString(dir.resolve("notice-no-code.md"), "# Sample content: A notice\n\nText.\n");
    Files.writeString(dir.resolve("claims.md"), "# Sample content: Claims (NLRTM)\n\nText.\n");

    List<PortNotices.Notice> notices = PortNotices.load(dir);

    assertThat(notices)
        .containsExactly(
            new PortNotices.Notice(
                "NLRTM",
                "Port notice, Rotterdam (NLRTM)",
                "Berths are full.",
                "notice-rotterdam-berths.md"));
  }

  @Test
  void aMissingFolderHasNoNotices(@TempDir Path dir) {
    assertThat(PortNotices.load(dir.resolve("missing"))).isEmpty();
  }
}
