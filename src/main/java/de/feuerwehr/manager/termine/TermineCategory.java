package de.feuerwehr.manager.termine;

public enum TermineCategory {
    DIENSTPLAN("dienstplan"),
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
}
