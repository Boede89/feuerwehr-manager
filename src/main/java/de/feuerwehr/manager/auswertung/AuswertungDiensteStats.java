package de.feuerwehr.manager.auswertung;

/** Kennzahlen für das Dashboard „Einsätze / Dienste“. */
public record AuswertungDiensteStats(
        int anzahl,
        String gesamtStunden,
        String avgPersonal,
        String avgGruppenfuehrer,
        String avgZugfuehrer,
        String avgDauerStunden,
        int einsaetzeCount,
        int uebungenCount,
        int sonderdienstCount,
        int sonstigesCount,
        AuswertungDiensteFilter filter) {

    public static AuswertungDiensteStats empty(AuswertungDiensteFilter filter) {
        return new AuswertungDiensteStats(0, "0 Std.", "—", "—", "—", "—", 0, 0, 0, 0, filter);
    }
}
