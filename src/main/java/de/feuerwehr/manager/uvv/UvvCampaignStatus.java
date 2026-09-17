package de.feuerwehr.manager.uvv;

public enum UvvCampaignStatus {
    OPEN,
    CLOSED;

    public String label() {
        return switch (this) {
            case OPEN -> "Offen";
            case CLOSED -> "Abgeschlossen";
        };
    }
}
