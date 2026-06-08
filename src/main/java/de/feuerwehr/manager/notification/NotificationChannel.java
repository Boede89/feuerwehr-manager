package de.feuerwehr.manager.notification;

public enum NotificationChannel {
    EMAIL("E-Mail");

    private final String label;

    NotificationChannel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String paramKey() {
        return name().toLowerCase();
    }
}
