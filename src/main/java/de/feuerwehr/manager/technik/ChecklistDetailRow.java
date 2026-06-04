package de.feuerwehr.manager.technik;

import java.time.Instant;
import java.util.List;

public record ChecklistDetailRow(
        Long id,
        String templateName,
        Instant filledAt,
        String filledName,
        String notes,
        List<ChecklistDetailEntryRow> entries) {}
