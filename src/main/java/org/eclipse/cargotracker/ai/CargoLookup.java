package org.eclipse.cargotracker.ai;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Read-only cargo data for the assistant tools. */
public interface CargoLookup {

  Optional<CargoStatusDetails> cargoStatus(String trackingId);

  List<CargoSummary> listCargos();

  List<AttentionItem> needingAttention();

  List<CargoSummary> atLocation(String location);

  boolean exists(String trackingId);

  Set<String> knownTrackingIds();
}
