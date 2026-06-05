package de.feuerwehr.manager.personal;

public enum AttendanceServiceType {
    EINSATZ("Einsatz"),
    UEBUNGSDIENST("Übungsdienst"),
    SONSTIGES("Sonstiges");

    private final String label;

    AttendanceServiceType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
