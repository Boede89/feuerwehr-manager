package de.feuerwehr.manager.berichte;

public enum GeraetewartEventArt {
    BRANDEINSATZ("Brandeinsatz"),
    TECH_HILFE("Techn. Hilfe"),
    CBRN("CBRN"),
    SONSTIGES("Sonstiges");

    private final String label;

    GeraetewartEventArt(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static GeraetewartEventArt fromKey(String key) {
        if (key == null || key.isBlank()) {
            return BRANDEINSATZ;
        }
        try {
            return valueOf(key.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BRANDEINSATZ;
        }
    }
}
