package de.feuerwehr.manager.berichte;

public enum GeraetewartTyp {
    EINSATZ("Einsatz"),
    UEBUNG("Übung");

    private final String label;

    GeraetewartTyp(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static GeraetewartTyp fromKey(String key) {
        if (key == null || key.isBlank()) {
            return UEBUNG;
        }
        try {
            return valueOf(key.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UEBUNG;
        }
    }
}
