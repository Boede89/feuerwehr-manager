package de.feuerwehr.manager.reservierungen;

import java.util.Locale;

public enum ReservierungenTab {
    UEBERSICHT("uebersicht", "Übersicht"),
    FAHRZEUGE("fahrzeuge", "Fahrzeuge"),
    RAEUME("raeume", "Räume"),
    MEINE("meine", "Meine Anträge"),
    VERWALTUNG("verwaltung", "Verwaltung");

    private final String key;
    private final String label;

    ReservierungenTab(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public static ReservierungenTab fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return UEBERSICHT;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ReservierungenTab tab : values()) {
            if (tab.key.equals(normalized)) {
                return tab;
            }
        }
        throw new IllegalArgumentException("Unbekannter Reservierungen-Tab: " + raw);
    }
}
