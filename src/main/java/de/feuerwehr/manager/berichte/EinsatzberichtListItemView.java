package de.feuerwehr.manager.berichte;

import java.time.LocalDate;

public record EinsatzberichtListItemView(
        long id,
        String incidentNumber,
        LocalDate incidentDate,
        String stichwort,
        String location,
        String statusKey,
        String statusLabel,
        boolean diveraSource,
        Long createdByUserId) {}
