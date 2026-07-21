package de.feuerwehr.manager.reservierungen;

public enum ReservationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    public boolean blocksAvailability() {
        return this == APPROVED;
    }
}
