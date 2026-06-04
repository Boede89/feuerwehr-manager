package de.feuerwehr.manager.technik;

import java.util.List;

public record ChecklistTemplateRow(
        Long id,
        String name,
        String intervalKey,
        String intervalLabel,
        int itemCount,
        List<ChecklistTemplateItemRow> items,
        String itemsJson) {}
