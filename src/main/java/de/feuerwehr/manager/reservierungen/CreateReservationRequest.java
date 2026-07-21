package de.feuerwehr.manager.reservierungen;

import java.time.Instant;

public record CreateReservationRequest(
        Long resourceId,
        String requesterName,
        String requesterEmail,
        String reason,
        String location,
        Instant startAt,
        Instant endAt,
        boolean forceAvailabilityOverride) {}
