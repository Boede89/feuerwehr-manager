package de.feuerwehr.manager.termine;

public enum TermineTab {
    DIENSTPLAN("dienstplan", "Dienstplan"),
    FAHRZEUGE("fahrzeuge", "Fahrzeuge"),
    SONSTIGES("sonstiges", "Sonstiges");

    private final String key;
    private final String label;

    TermineTab(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public static TermineTab fromKey(String key) {
        if (key == null || key.isBlank()) {
            return DIENSTPLAN;
        }
        for (TermineTab tab : values()) {
            if (tab.key.equalsIgnoreCase(key.trim())) {
                return tab;
            }
        }
        return DIENSTPLAN;
    }
}
