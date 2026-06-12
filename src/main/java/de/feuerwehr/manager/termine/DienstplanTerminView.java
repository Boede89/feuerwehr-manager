package de.feuerwehr.manager.termine;

import java.time.LocalDate;
import java.time.LocalTime;

public record DienstplanTerminView(
        long id,
        LocalDate datum,
        String thema,
        LocalTime dienstBeginn,
        LocalTime dienstEnde,
        String ausbilderName,
        String personenGruppenLabel) {}
