package de.feuerwehr.manager.reservierungen;

import java.util.List;

/** JSON-Export aus der alten feuerwehr-app (PHP). */
public record LegacyReservationExportFile(
        int formatVersion,
        String source,
        String exportedAt,
        Integer reservationCount,
        List<LegacyReservationExportItem> reservations) {}
