package de.feuerwehr.manager.personal;

public enum AttendanceStatus {
    PRESENT("Anwesend"),
    ABSENT("Abwesend"),
    EXCUSED("Entschuldigt");

    private final String label;

    AttendanceStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
