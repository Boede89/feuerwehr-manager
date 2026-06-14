package de.feuerwehr.manager.berichte;

import java.time.LocalDate;

public record MaengelberichtListItemView(
        long id,
        LocalDate aufgenommenAm,
        String standortLabel,
        String mangelAnLabel,
        String bezeichnung,
        String recordedByDisplay,
        Long createdByUserId) {}
