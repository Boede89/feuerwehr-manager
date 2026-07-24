package de.feuerwehr.manager.auswertung;

import java.util.Locale;

/** Auswählbare Detail-Ansicht unter den Übersichtskacheln. */
public enum AuswertungOverviewDetail {
    EINSAETZE("einsaetze", "Einsätze"),
    FEUER("feuer", "Feuer"),
    TH("th", "TH"),
    CBRN("cbrn", "CBRN"),
    SONSTIGES("sonstiges", "Sonstiges");

    private final String key;
    private final String label;

    AuswertungOverviewDetail(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public static AuswertungOverviewDetail fromKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (AuswertungOverviewDetail detail : values()) {
            if (detail.key.equals(normalized)) {
                return detail;
            }
        }
        return null;
    }

    public boolean matches(AuswertungStichwortKategorie.Kategorie kategorie) {
        return switch (this) {
            case EINSAETZE -> true;
            case FEUER -> kategorie == AuswertungStichwortKategorie.Kategorie.FEUER;
            case TH -> kategorie == AuswertungStichwortKategorie.Kategorie.TH;
            case CBRN -> kategorie == AuswertungStichwortKategorie.Kategorie.CBRN;
            case SONSTIGES -> kategorie == AuswertungStichwortKategorie.Kategorie.SONSTIGES;
        };
    }
}
