package de.feuerwehr.manager.atemschutz;

public enum CarrierTauglichkeitStatus {
    TAUGLICH,
    UEBUNG_ABGELAUFEN,
    NICHT_TAUGLICH;

    public String label() {
        return switch (this) {
            case TAUGLICH -> "Tauglich";
            case UEBUNG_ABGELAUFEN -> "Übung abgelaufen";
            case NICHT_TAUGLICH -> "Nicht tauglich";
        };
    }

    public String badgeClass() {
        return switch (this) {
            case TAUGLICH -> "badge active";
            case UEBUNG_ABGELAUFEN -> "badge-warning";
            case NICHT_TAUGLICH -> "badge inactive";
        };
    }
}
