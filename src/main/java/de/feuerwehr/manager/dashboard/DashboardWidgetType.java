package de.feuerwehr.manager.dashboard;

import de.feuerwehr.manager.settings.AppModule;

/**
 * Verfügbare Startseiten-Widgets. Sichtbarkeit zusätzlich über Modul + Rechte.
 */
public enum DashboardWidgetType {
    MY_STATS(
            "Meine Beteiligung",
            "Persönliche Übungsdienst- und Einsatzquote im laufenden Jahr",
            null,
            null,
            false),
    DIVERA(
            "Aktuelle Einsätze",
            "Laufende Einsätze aus Divera (und manuelle Alarme)",
            null,
            null,
            false),
    TERMINE(
            "Meine Termine",
            "Kommende Termine Ihrer Person",
            AppModule.TERMINE,
            "termine.read",
            false),
    PLANNED_ALARMS(
            "Geplante Einsätze",
            "Noch nicht gestartete manuelle Einsätze",
            null,
            null,
            true),
    UNIT_OVERVIEW(
            "Einheiten-Kennzahlen",
            "Einsätze, Übungsdienste und Mitglieder der Einheit",
            AppModule.AUSWERTUNG,
            "auswertung.read",
            false);

    private final String label;
    private final String description;
    private final AppModule requiredModule;
    private final String requiredPermission;
    private final boolean adminOnly;

    DashboardWidgetType(
            String label,
            String description,
            AppModule requiredModule,
            String requiredPermission,
            boolean adminOnly) {
        this.label = label;
        this.description = description;
        this.requiredModule = requiredModule;
        this.requiredPermission = requiredPermission;
        this.adminOnly = adminOnly;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public AppModule requiredModule() {
        return requiredModule;
    }

    public String requiredPermission() {
        return requiredPermission;
    }

    public boolean adminOnly() {
        return adminOnly;
    }

    public static DashboardWidgetType fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return DashboardWidgetType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
