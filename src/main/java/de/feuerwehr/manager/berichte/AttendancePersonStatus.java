package de.feuerwehr.manager.berichte;

public enum AttendancePersonStatus {
    PRESENT,
    ABSENT,
    EXCUSED;

    public String label() {
        return switch (this) {
            case PRESENT -> "Anwesend";
            case ABSENT -> "Abwesend";
            case EXCUSED -> "Entschuldigt";
        };
    }
}
