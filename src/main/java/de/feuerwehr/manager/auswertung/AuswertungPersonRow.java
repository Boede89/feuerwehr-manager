package de.feuerwehr.manager.auswertung;

import java.util.List;

/** Zeile in der Personen-Auswertungstabelle. */
public record AuswertungPersonRow(
        long personId,
        String name,
        boolean archived,
        String dienstbeteiligung,
        String einsatzbeteiligung,
        double dienstPct,
        double einsatzPct,
        String dienstQuote,
        String einsatzQuote,
        List<AuswertungPersonTeilnahme> dienste,
        List<AuswertungPersonTeilnahme> einsaetze,
        List<AuswertungPersonTeilnahme> verpassteDienste,
        List<AuswertungPersonTeilnahme> verpassteEinsaetze) {}
