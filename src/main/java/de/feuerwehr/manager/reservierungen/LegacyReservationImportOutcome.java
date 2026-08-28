package de.feuerwehr.manager.reservierungen;

import java.util.List;

public record LegacyReservationImportOutcome(int imported, int skipped, List<String> details) {}
