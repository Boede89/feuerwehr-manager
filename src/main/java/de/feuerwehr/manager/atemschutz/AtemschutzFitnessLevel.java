package de.feuerwehr.manager.atemschutz;

public enum AtemschutzFitnessLevel {
    OK,
    WARN,
    OVERDUE,
    MISSING;

    public String label() {
        return switch (this) {
            case OK -> "OK";
            case WARN -> "Bald fällig";
            case OVERDUE -> "Überfällig";
            case MISSING -> "Fehlt";
        };
    }

    public String cssClass() {
        return switch (this) {
            case OK -> "fitness-badge--ok";
            case WARN -> "fitness-badge--warn";
            case OVERDUE -> "fitness-badge--overdue";
            case MISSING -> "fitness-badge--missing";
        };
    }
}
