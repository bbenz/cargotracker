package org.eclipse.cargotracker.ai;

import java.util.List;

public record CargoStatusDetails(
    String trackingId,
    String origin,
    String destination,
    String routingStatus,
    String transportStatus,
    String lastKnownLocation,
    String nextExpectedActivity,
    String eta,
    String arrivalDeadline,
    boolean misdirected,
    String attention,
    List<String> itinerary) {}
