package org.eclipse.cargotracker.interfaces.dashboard;

import java.util.Map;
import java.util.Optional;
import org.eclipse.cargotracker.interfaces.Coordinates;
import org.eclipse.cargotracker.interfaces.CoordinatesFactory;

/** Map positions for the sample locations. Inland places connect by rail or air, not by sea. */
public final class PortCoordinates {

  public record Position(double latitude, double longitude, boolean inland) {}

  private static final Map<String, Position> POSITIONS =
      Map.ofEntries(
          Map.entry("CNHKG", new Position(22.30, 114.17, false)),
          Map.entry("AUMEL", new Position(-37.84, 144.93, false)),
          Map.entry("SESTO", new Position(59.33, 18.07, false)),
          Map.entry("FIHEL", new Position(60.16, 24.95, false)),
          Map.entry("USCHI", new Position(41.88, -87.63, true)),
          Map.entry("JNTKO", new Position(35.62, 139.78, false)),
          Map.entry("DEHAM", new Position(53.54, 9.98, false)),
          Map.entry("CNSHA", new Position(31.23, 121.49, false)),
          Map.entry("NLRTM", new Position(51.95, 4.14, false)),
          Map.entry("SEGOT", new Position(57.69, 11.92, false)),
          Map.entry("CNHGH", new Position(30.27, 120.16, false)),
          Map.entry("USNYC", new Position(40.68, -74.04, false)),
          Map.entry("USDAL", new Position(32.78, -96.80, true)),
          Map.entry("SGSIN", new Position(1.26, 103.82, false)),
          Map.entry("USLAX", new Position(33.74, -118.27, false)),
          Map.entry("AEJEA", new Position(25.01, 55.06, false)),
          Map.entry("BRSSZ", new Position(-23.96, -46.30, false)),
          Map.entry("KRPUS", new Position(35.10, 129.04, false)),
          Map.entry("INBOM", new Position(18.95, 72.84, false)),
          Map.entry("ZADUR", new Position(-29.87, 31.03, false)),
          Map.entry("ESALG", new Position(36.13, -5.44, false)));

  private PortCoordinates() {}

  public static Optional<Position> find(String unLocode) {
    Position position = POSITIONS.get(unLocode);
    if (position != null) {
      return Optional.of(position);
    }
    // Fall back to the coarse table the original live map uses.
    Coordinates coordinates = CoordinatesFactory.find(unLocode);
    return coordinates == null
        ? Optional.empty()
        : Optional.of(new Position(coordinates.getLatitude(), coordinates.getLongitude(), false));
  }
}
