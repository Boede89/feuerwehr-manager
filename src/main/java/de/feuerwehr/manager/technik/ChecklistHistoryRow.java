package de.feuerwehr.manager.technik;

import java.time.Instant;

public record ChecklistHistoryRow(
        Long id,
        String templateName,
        Instant filledAt,
        String filledName,
        int okCount,
        int mangelCount) {}
