package de.feuerwehr.manager.uvv;

public enum UvvCompletionChannel {
    PRESENCE,
    ONLINE,
    MANUAL;

    public String label() {
        return switch (this) {
            case PRESENCE -> "Präsenz";
            case ONLINE -> "Online";
            case MANUAL -> "Manuell";
        };
    }
}
