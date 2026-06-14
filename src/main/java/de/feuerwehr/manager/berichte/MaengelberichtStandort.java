package de.feuerwehr.manager.berichte;

public enum MaengelberichtStandort {
    GH_AMERN("GH Amern"),
    GH_HEHLER("GH Hehler"),
    GH_WALDNIEL("GH Waldniel");

    private final String label;

    MaengelberichtStandort(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static MaengelberichtStandort fromKey(String key) {
        if (key == null || key.isBlank()) {
            return GH_AMERN;
        }
        for (MaengelberichtStandort value : values()) {
            if (value.name().equalsIgnoreCase(key.trim()) || value.label.equalsIgnoreCase(key.trim())) {
                return value;
            }
        }
        try {
            return valueOf(key.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return GH_AMERN;
        }
    }
}
