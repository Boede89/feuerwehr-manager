package de.feuerwehr.manager.auswertung;

/** Kennzahlen für die Übersichtsseite. */
public record AuswertungOverviewStats(
        int einsaetze,
        String einsaetzeStunden,
        int feuer,
        String feuerStunden,
        int th,
        String thStunden,
        int cbrn,
        String cbrnStunden,
        int sonstiges,
        String sonstigesStunden,
        int uebungsdienste,
        String uebungsdiensteStunden,
        int mitglieder,
        int tauglichePaTraeger) {

    public static AuswertungOverviewStats empty() {
        return new AuswertungOverviewStats(
                0, "0 Std.", 0, "0 Std.", 0, "0 Std.", 0, "0 Std.", 0, "0 Std.", 0, "0 Std.", 0, 0);
    }
}
