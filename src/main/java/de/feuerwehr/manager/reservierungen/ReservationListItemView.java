package de.feuerwehr.manager.reservierungen;

import java.time.Instant;
import java.util.List;

public record ReservationListItemView(
        long id,
        ReservationKind kind,
        String resourceName,
        String requesterName,
        String requesterEmail,
        String reason,
        String location,
        Instant startAt,
        Instant endAt,
        ReservationStatus status,
        String rejectionReason,
        Instant approvedAt,
        String approvedByName,
        Instant createdAt,
        boolean ownedByCurrentUser,
        boolean hasConflict,
        List<ReservationResourceItem> resources) {}
