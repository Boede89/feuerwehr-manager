package de.feuerwehr.manager.auswertung;

/** Kennzahlen für die Übersichtsseite. */
public record AuswertungOverviewStats(
        int einsaetze,
        int feuer,
        int th,
        int sonstiges,
        int uebungsdienste,
        int mitglieder,
        int tauglichePaTraeger) {

    public static AuswertungOverviewStats empty() {
        return new AuswertungOverviewStats(0, 0, 0, 0, 0, 0, 0);
    }
}
