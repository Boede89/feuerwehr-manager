package de.feuerwehr.manager.drivinglicense;

public enum DrivingLicenseStatusLevel {
    OK,
    WARN,
    OVERDUE,
    PENDING,
    MISSING,
    NONE;

    public String label() {
        return switch (this) {
            case OK -> "OK";
            case WARN -> "Bald fällig";
            case OVERDUE -> "Überfällig";
            case PENDING -> "Kontrolle ausstehend";
            case MISSING -> "Angaben fehlen";
            case NONE -> "Nicht relevant";
        };
    }

    public String cssClass() {
        return switch (this) {
            case OK -> "fitness-badge--ok";
            case WARN, PENDING -> "fitness-badge--warn";
            case OVERDUE -> "fitness-badge--overdue";
            case MISSING, NONE -> "fitness-badge--missing";
        };
    }

    public String validityBadgeClass() {
        return switch (this) {
            case OK -> "badge active";
            case WARN, PENDING -> "badge-warning";
            case OVERDUE -> "badge inactive";
            case MISSING, NONE -> "";
        };
    }
}
