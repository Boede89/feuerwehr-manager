package de.feuerwehr.manager.drivinglicense;

public enum DrivingLicenseStatusLevel {
    OK,
    WARN,
    OVERDUE,
    MISSING,
    NONE;

    public String label() {
        return switch (this) {
            case OK -> "OK";
            case WARN -> "Bald fällig";
            case OVERDUE -> "Überfällig";
            case MISSING -> "Kontrolle fehlt";
            case NONE -> "Nicht relevant";
        };
    }

    public String cssClass() {
        return switch (this) {
            case OK -> "fitness-badge--ok";
            case WARN -> "fitness-badge--warn";
            case OVERDUE -> "fitness-badge--overdue";
            case MISSING -> "fitness-badge--missing";
            case NONE -> "fitness-badge--missing";
        };
    }

    public String validityBadgeClass() {
        return switch (this) {
            case OK -> "badge active";
            case WARN -> "badge-warning";
            case OVERDUE -> "badge inactive";
            case MISSING, NONE -> "";
        };
    }
}
