package de.feuerwehr.manager.atemschutz;

public enum AtemschutzEntryRequestStatus {
    PENDING,
    APPROVED,
    REJECTED;

    public String label() {
        return switch (this) {
            case PENDING -> "Offen";
            case APPROVED -> "Genehmigt";
            case REJECTED -> "Abgelehnt";
        };
    }
}
