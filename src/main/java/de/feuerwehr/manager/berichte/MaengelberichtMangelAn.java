package de.feuerwehr.manager.berichte;

public enum MaengelberichtMangelAn {
    GEBAEUDE("Gebäude"),
    FAHRZEUG("Fahrzeug"),
    GERAET("Gerät"),
    PSA("PSA");

    private final String label;

    MaengelberichtMangelAn(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static MaengelberichtMangelAn fromKey(String key) {
        if (key == null || key.isBlank()) {
            return GEBAEUDE;
        }
        for (MaengelberichtMangelAn value : values()) {
            if (value.name().equalsIgnoreCase(key.trim()) || value.label.equalsIgnoreCase(key.trim())) {
                return value;
            }
        }
        try {
            return valueOf(key.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return GEBAEUDE;
        }
    }
}
