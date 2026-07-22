package de.feuerwehr.manager.termine;

public enum TermineTab {
    MEINE("meine", "Meine Termine"),
    DIENSTPLAN("dienstplan", "Übungsdienst"),
    SONDERDIENST("sonderdienst", "Sonderdienst"),
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
            return MEINE;
        }
        for (TermineTab tab : values()) {
            if (tab.key.equalsIgnoreCase(key.trim())) {
                return tab;
            }
        }
        return MEINE;
    }
}
