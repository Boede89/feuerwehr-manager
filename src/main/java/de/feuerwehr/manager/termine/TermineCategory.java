package de.feuerwehr.manager.termine;

public enum TermineCategory {
    DIENSTPLAN("dienstplan"),
    SONDERDIENST("sonderdienst"),
    FAHRZEUGE("fahrzeuge"),
    SONSTIGES("sonstiges");

    private final String key;

    TermineCategory(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static TermineCategory fromKey(String key) {
        if (key == null || key.isBlank()) {
            return DIENSTPLAN;
        }
        for (TermineCategory category : values()) {
            if (category.key.equalsIgnoreCase(key.trim())) {
                return category;
            }
        }
        return DIENSTPLAN;
    }

    public String displayLabel() {
        return switch (this) {
            case DIENSTPLAN -> "Übungsdienst";
            case SONDERDIENST -> "Sonderdienst";
            case FAHRZEUGE -> "Fahrzeuge";
            case SONSTIGES -> "Sonstiges";
        };
    }

    /** Kategorien, aus denen Anwesenheitslisten erzeugt werden können. */
    public boolean supportsAttendanceReports() {
        return this == DIENSTPLAN || this == SONDERDIENST || this == SONSTIGES;
    }
}
