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

    public String description() {
        return switch (this) {
            case UEBERSICHT -> "Kennzahlen und Einstieg";
            case PERSONEN -> "Teilnahmen, Stunden, Maschinist/EF, letzte Teilnahme";
            case EINSAETZE -> "Anzahl, Stunden, Stichworte, Durchschnittsstärke";
            case FAHRZEUGE -> "Einsätze und Übungen je Fahrzeug, Besatzung";
            case GERAETE -> "Welche Geräte wie oft eingesetzt wurden";
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
