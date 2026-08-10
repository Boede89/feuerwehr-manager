package de.feuerwehr.manager.reservierungen;

import java.util.List;

/** Ausnahme bei zeitlichen Konflikten — UI kann Konfliktlösung anbieten. */
public class ReservationConflictException extends IllegalStateException {

    private final List<ReservationConflictView> conflicts;
    private final List<Long> conflictingResourceIds;

    public ReservationConflictException(String message, List<ReservationConflictView> conflicts) {
        this(message, conflicts, List.of());
    }

    public ReservationConflictException(
            String message, List<ReservationConflictView> conflicts, List<Long> conflictingResourceIds) {
        super(message);
        this.conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        this.conflictingResourceIds =
                conflictingResourceIds == null ? List.of() : List.copyOf(conflictingResourceIds);
    }

    public List<ReservationConflictView> getConflicts() {
        return conflicts;
    }

    public List<Long> getConflictingResourceIds() {
        return conflictingResourceIds;
    }
}
