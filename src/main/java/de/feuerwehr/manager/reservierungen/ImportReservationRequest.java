package de.feuerwehr.manager.reservierungen;

import java.time.Instant;
import java.util.List;

/** Übernahme bestehender Reservierungen (direkt genehmigt). */
public record ImportReservationRequest(
        String kind,
        List<Long> resourceIds,
        Long resourceId,
        String requesterName,
        String requesterEmail,
        String reason,
        String location,
        Instant startAt,
        Instant endAt,
        boolean sendRequesterEmail,
        boolean syncCalendars) {}
