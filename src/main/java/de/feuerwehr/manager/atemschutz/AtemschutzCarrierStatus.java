package de.feuerwehr.manager.atemschutz;

public enum AtemschutzCarrierStatus {
    ACTIVE,
    PAUSED;

    public String label() {
        return switch (this) {
            case ACTIVE -> "Aktiv";
            case PAUSED -> "Pausiert";
        };
    }
}
