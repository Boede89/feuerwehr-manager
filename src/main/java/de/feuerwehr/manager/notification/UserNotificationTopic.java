package de.feuerwehr.manager.notification;

public enum UserNotificationTopic {
    ATEMSCHUTZ("Atemschutz", "Erinnerungen, Terminplanung und Mitteilungen zum Atemschutz"),
    FUEHRERSCHEIN("Führerschein", "Erinnerungen zur jährlichen Führerscheinkontrolle"),
    UVV("UVV / Belehrung", "Erinnerungen zur jährlichen UVV-/Kraftfahrer-Belehrung");

    private final String label;
    private final String description;

    UserNotificationTopic(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public String paramKey() {
        return name().toLowerCase();
    }
}
