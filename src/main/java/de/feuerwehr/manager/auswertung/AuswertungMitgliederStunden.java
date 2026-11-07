package de.feuerwehr.manager.auswertung;

import java.util.List;

/** Mitgliederstunden zur gewählten Ereignisauswahl, inklusive Spaltensummen. */
public record AuswertungMitgliederStunden(
        List<AuswertungStundenRow> rows,
        String einsatzStunden,
        String uebungStunden,
        String sonderStunden,
        String sonstigStunden,
        String summeStunden) {

    public static AuswertungMitgliederStunden empty() {
        return new AuswertungMitgliederStunden(List.of(), "0 Std.", "0 Std.", "0 Std.", "0 Std.", "0 Std.");
    }
}
