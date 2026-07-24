package de.feuerwehr.manager.auswertung;

/** Zeile in der Personen-Auswertungstabelle. */
public record AuswertungPersonRow(
        long personId,
        String name,
        String dienstbeteiligung,
        String einsatzbeteiligung,
        double dienstPct,
        double einsatzPct) {}
