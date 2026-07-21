package de.feuerwehr.manager.reservierungen;

import java.time.Instant;

public record ReservationConflictView(
        long id,
        ReservationKind kind,
        String resourceName,
        String requesterName,
        Instant startAt,
        Instant endAt,
        ReservationStatus status) {}
