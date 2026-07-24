package de.feuerwehr.manager.auswertung;

public enum AuswertungBereich {
    UEBERSICHT(""),
    PERSONEN("personen"),
    EINSAETZE("einsaetze"),
    FAHRZEUGE("fahrzeuge"),
    GERAETE("geraete");

    private final String key;

    AuswertungBereich(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String label() {
        return switch (this) {
            case UEBERSICHT -> "Übersicht";
            case PERSONEN -> "Personen";
            case EINSAETZE -> "Einsätze / Dienste";
            case FAHRZEUGE -> "Fahrzeuge";
            case GERAETE -> "Geräte";
        };
    }

    public static AuswertungBereich fromKey(String key) {
        if (key == null || key.isBlank()) {
            return UEBERSICHT;
        }
        for (AuswertungBereich bereich : values()) {
            if (bereich.key.equalsIgnoreCase(key.trim())) {
                return bereich;
            }
        }
        return UEBERSICHT;
    }
}
