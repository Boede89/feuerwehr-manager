package de.feuerwehr.manager.reservierungen;

import java.time.Instant;
import java.util.List;

/** Änderung einer bereits genehmigten Reservierung. */
public record UpdateReservationRequest(
        List<Long> resourceIds,
        Long resourceId,
        String requesterName,
        String requesterEmail,
        String reason,
        String location,
        Instant startAt,
        Instant endAt,
        boolean sendRequesterEmail,
        boolean forceAvailabilityOverride,
        boolean forceConflictOverride) {}
