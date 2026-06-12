package de.feuerwehr.manager.berichte;

import java.time.LocalDate;

public record AnwesenheitslisteListItemView(
        long id,
        String reportNumber,
        LocalDate eventDate,
        String title,
        String terminCategoryKey,
        String terminCategoryLabel,
        String location,
        String statusKey,
        String statusLabel,
        boolean terminSource,
        Long createdByUserId) {}
