package de.feuerwehr.manager.reservierungen;

import java.util.List;

/** Ausnahme bei zeitlichen Konflikten — UI kann Konfliktlösung anbieten. */
public class ReservationConflictException extends IllegalStateException {

    private final List<ReservationConflictView> conflicts;

    public ReservationConflictException(String message, List<ReservationConflictView> conflicts) {
        super(message);
        this.conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    public List<ReservationConflictView> getConflicts() {
        return conflicts;
    }
}
