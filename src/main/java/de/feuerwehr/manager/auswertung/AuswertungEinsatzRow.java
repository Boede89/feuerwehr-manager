package de.feuerwehr.manager.auswertung;

import java.time.LocalDate;

/** Zeile in der Einsatz-Detailtabelle der Übersicht. */
public record AuswertungEinsatzRow(
        LocalDate datum,
        String stichwort,
        String dauerStunden,
        int personal,
        int zf,
        int gf) {}
