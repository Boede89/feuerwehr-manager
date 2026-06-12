package de.feuerwehr.manager.berichte;

public enum GeraetewartReadiness {
    HERGESTELLT("Einsatzbereitschaft wurde hergestellt"),
    NICHT_HERGESTELLT("Einsatzbereitschaft wurde nicht hergestellt");

    private final String label;

    GeraetewartReadiness(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static GeraetewartReadiness fromKey(String key) {
        if (key == null || key.isBlank()) {
            return HERGESTELLT;
        }
        try {
            return valueOf(key.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return HERGESTELLT;
        }
    }
}
