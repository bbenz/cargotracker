package org.eclipse.cargotracker.ai;

/** A cargo that needs attention, with a plain-language explanation. */
public record AttentionItem(String trackingId, String issue, String details) {}
