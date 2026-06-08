package de.feuerwehr.manager.atemschutz;

public enum CarrierTauglichkeitStatus {
    TAUGLICH,
    WARNUNG,
    UEBUNG_ABGELAUFEN,
    NICHT_TAUGLICH;

    public String label() {
        return switch (this) {
            case TAUGLICH -> "Tauglich";
            case WARNUNG -> "Warnung";
            case UEBUNG_ABGELAUFEN -> "Übung abgelaufen";
            case NICHT_TAUGLICH -> "Nicht tauglich";
        };
    }

    public String badgeClass() {
        return switch (this) {
            case TAUGLICH -> "badge active";
            case WARNUNG -> "badge warn";
            case UEBUNG_ABGELAUFEN -> "badge warn-uebung";
            case NICHT_TAUGLICH -> "badge inactive";
        };
    }
}
