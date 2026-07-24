package de.feuerwehr.manager.auswertung;

/** Platzhalter-Kennzahlen für die Übersichtsseite (Zähllogik folgt später). */
public record AuswertungOverviewStats(
        int einsaetze, int feuer, int th, int sonstiges, int uebungsdienste) {

    public static AuswertungOverviewStats empty() {
        return new AuswertungOverviewStats(0, 0, 0, 0, 0);
    }
}
