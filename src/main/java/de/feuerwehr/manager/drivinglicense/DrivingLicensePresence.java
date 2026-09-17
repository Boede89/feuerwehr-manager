package de.feuerwehr.manager.drivinglicense;

public enum DrivingLicensePresence {
    UNKNOWN,
    YES,
    NO;

    public String label() {
        return switch (this) {
            case UNKNOWN -> "Unbekannt";
            case YES -> "Vorhanden";
            case NO -> "Kein Führerschein";
        };
    }
}
