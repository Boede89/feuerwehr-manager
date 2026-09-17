package de.feuerwehr.manager.drivinglicense;

public enum DrivingLicenseCheckResult {
    VALID,
    RESTRICTED,
    INVALID;

    public String label() {
        return switch (this) {
            case VALID -> "Gültig";
            case RESTRICTED -> "Eingeschränkt";
            case INVALID -> "Nicht gültig";
        };
    }
}
