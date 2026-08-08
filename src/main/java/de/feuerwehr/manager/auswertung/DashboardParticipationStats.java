package de.feuerwehr.manager.auswertung;

/**
 * Persönliche Beteiligungsstatistik für die Startseite
 * (gleiche Regeln wie Personen-Auswertung).
 */
public record DashboardParticipationStats(
        int year,
        String personName,
        int uebungenAttended,
        int uebungenTotal,
        String uebungQuote,
        String uebungPct,
        double uebungPctValue,
        int einsaetzeAttended,
        int einsaetzeTotal,
        String einsatzQuote,
        String einsatzPct,
        double einsatzPctValue) {}
