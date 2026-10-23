package de.feuerwehr.manager.auswertung;

/**
 * Auswahl der Ereignisarten für die Auswertung „Einsätze / Dienste“.
 * Standard: Einsätze und Übungsdienste aktiv, Sonderdienst/Sonstiges aus.
 */
public record AuswertungDiensteFilter(
        boolean einsaetze, boolean uebungen, boolean sonderdienste, boolean sonstiges) {

    public static AuswertungDiensteFilter defaults() {
        return new AuswertungDiensteFilter(true, true, false, false);
    }

    /** Checkbox-Parameter: fehlend/`false` = aus, nur explizites true = an. */
    public static AuswertungDiensteFilter fromParams(
            Boolean einsaetze, Boolean uebungen, Boolean sonderdienste, Boolean sonstiges) {
        return new AuswertungDiensteFilter(
                Boolean.TRUE.equals(einsaetze),
                Boolean.TRUE.equals(uebungen),
                Boolean.TRUE.equals(sonderdienste),
                Boolean.TRUE.equals(sonstiges));
    }

    public boolean anySelected() {
        return einsaetze || uebungen || sonderdienste || sonstiges;
    }
}
