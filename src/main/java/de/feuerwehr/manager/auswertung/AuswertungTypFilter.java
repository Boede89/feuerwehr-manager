package de.feuerwehr.manager.auswertung;

/** Filter für den Typ der ausgewerteten Ereignisse. */
public enum AuswertungTypFilter {
    BEIDES("beides"),
    EINSAETZE("einsaetze"),
    UEBUNGEN("uebungen"),
    SONSTIGES("sonstiges");

    private final String key;

    AuswertungTypFilter(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String label() {
        return switch (this) {
            case BEIDES -> "Einsätze + Übungen";
            case EINSAETZE -> "Nur Einsätze";
            case UEBUNGEN -> "Nur Übungen / Dienste";
            case SONSTIGES -> "Sonstiges";
        };
    }

    public static AuswertungTypFilter fromKey(String key) {
        if (key == null || key.isBlank()) {
            return BEIDES;
        }
        for (AuswertungTypFilter filter : values()) {
            if (filter.key.equalsIgnoreCase(key.trim())) {
                return filter;
            }
        }
        return BEIDES;
    }
}
