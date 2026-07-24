package de.feuerwehr.manager.auswertung;

import java.time.LocalDate;
import java.util.List;

/** Zeile in der Detailtabelle der Übersicht (inkl. Modal-Daten). */
public record AuswertungEinsatzRow(
        long reportId,
        String kind,
        LocalDate datum,
        String stichwort,
        String dauerStunden,
        int personal,
        int zf,
        int gf,
        String alarmzeit,
        String einsatzende,
        String leitungLabel,
        String leitung,
        List<String> personen,
        List<String> paTraeger,
        List<String> fahrzeuge,
        String viewUrl,
        String openButtonLabel) {

    public boolean hasPaTraeger() {
        return paTraeger != null && !paTraeger.isEmpty();
    }
}
