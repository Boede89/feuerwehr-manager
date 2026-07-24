package de.feuerwehr.manager.auswertung;

import java.util.Locale;

/** Klassifikation von Einsatzbericht-Stichworten für die Übersichtskennzahlen. */
public final class AuswertungStichwortKategorie {

    public enum Kategorie {
        FEUER,
        TH,
        CBRN,
        SONSTIGES
    }

    private AuswertungStichwortKategorie() {}

    /**
     * Regeln (nur Einsatzberichte):
     * <ul>
     *   <li>CBRN… → CBRN</li>
     *   <li>TH… → TH</li>
     *   <li>F… → Feuer</li>
     *   <li>alles andere (inkl. leer) → Sonstiges</li>
     * </ul>
     * Längere Präfixe zuerst, Vergleich case-insensitive.
     */
    public static Kategorie classify(String stichwort) {
        if (stichwort == null) {
            return Kategorie.SONSTIGES;
        }
        String normalized = stichwort.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Kategorie.SONSTIGES;
        }
        if (normalized.startsWith("CBRN")) {
            return Kategorie.CBRN;
        }
        if (normalized.startsWith("TH")) {
            return Kategorie.TH;
        }
        if (normalized.startsWith("F")) {
            return Kategorie.FEUER;
        }
        return Kategorie.SONSTIGES;
    }
}
