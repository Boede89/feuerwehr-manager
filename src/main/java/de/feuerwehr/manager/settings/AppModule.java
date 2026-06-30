package de.feuerwehr.manager.settings;

/** Aktivierbare Navigations-Module (Top-Leiste). */
public enum AppModule {
    PERSONAL("personal", "Personal", "Mitgliederverwaltung", "👥", true),
    RESERVIERUNGEN("reservierungen", "Reservierungen", "Fahrzeug- und Gerätereservierungen", "🚒", false),
    ATEMSCHUTZ("atemschutz", "Atemschutz", "Tauglichkeiten und Nachweise", "🛡️", true),
    BERICHTE("berichte", "Berichte", "Einsatz- und Dienstberichte", "📋", true),
    TERMINE("termine", "Termine", "Kalender und Terminplanung", "📅", true),
    EINSATZAPP("einsatzapp", "Einsatz-App", "Android-Alarmierung bei DIVERA-Einsätzen", "📱", true),
    AUSWERTUNG("auswertung", "Auswertung", "Statistiken und Auswertungen", "📊", false);

    private final String key;
    private final String label;
    private final String description;
    private final String icon;
    private final boolean implemented;

    AppModule(String key, String label, String description, String icon, boolean implemented) {
        this.key = key;
        this.label = label;
        this.description = description;
        this.icon = icon;
        this.implemented = implemented;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public String icon() {
        return icon;
    }

    public boolean implemented() {
        return implemented;
    }
}
