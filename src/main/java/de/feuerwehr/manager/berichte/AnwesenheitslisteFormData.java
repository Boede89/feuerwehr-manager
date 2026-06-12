package de.feuerwehr.manager.berichte;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AnwesenheitslisteFormData(
        String reportNumber,
        LocalDate eventDate,
        LocalTime startTime,
        LocalTime endTime,
        String title,
        String location,
        String notes,
        List<AnwesenheitslistePersonnelRow> personnel) {}
