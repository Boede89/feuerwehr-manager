package de.feuerwehr.manager.termine;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record MeineTerminView(
        long id,
        TermineCategory category,
        String categoryLabel,
        LocalDate datum,
        String thema,
        LocalTime beginn,
        LocalTime ende,
        String ausbilderName,
        LocalDateTime startAt,
        LocalDateTime endAt) {}
