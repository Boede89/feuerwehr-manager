package de.feuerwehr.manager.termine;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateDienstplanTerminRequest(
        LocalDate terminDatum,
        String thema,
        LocalTime dienstBeginn,
        LocalTime dienstEnde,
        Long instructorPersonId) {}
