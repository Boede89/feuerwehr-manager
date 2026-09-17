package de.feuerwehr.manager.mediathek;

public enum MediathekAccessLevel {
    READ,
    WRITE;

    public String label() {
        return switch (this) {
            case READ -> "Lesen";
            case WRITE -> "Schreiben";
        };
    }

    public boolean includes(MediathekAccessLevel required) {
        if (required == READ) {
            return this == READ || this == WRITE;
        }
        return this == WRITE;
    }
}
