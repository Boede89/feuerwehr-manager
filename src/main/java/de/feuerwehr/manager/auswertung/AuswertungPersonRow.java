package de.feuerwehr.manager.auswertung;

import java.util.List;

/** Zeile in der Personen-Auswertungstabelle. */
public record AuswertungPersonRow(
        long personId,
        String name,
        String dienstbeteiligung,
        String einsatzbeteiligung,
        double dienstPct,
        double einsatzPct,
        List<AuswertungPersonTeilnahme> dienste,
        List<AuswertungPersonTeilnahme> einsaetze) {}
