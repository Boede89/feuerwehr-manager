package de.feuerwehr.manager.auswertung;

/**
 * Kennzahlen für das Dashboard „Einsätze / Dienste“.
 * {@code gesamtStunden} und die Stunden je Art sind Personstunden
 * (Ereignisdauer × Anzahl beteiligter Personen).
 */
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
        String einsaetzeStunden,
        String uebungenStunden,
        String sonderdienstStunden,
        String sonstigesStunden,
        AuswertungDiensteFilter filter) {

    public static AuswertungDiensteStats empty(AuswertungDiensteFilter filter) {
        return new AuswertungDiensteStats(
                0, "0 Std.", "—", "—", "—", "—", 0, 0, 0, 0, "0 Std.", "0 Std.", "0 Std.", "0 Std.", filter);
    }
}
