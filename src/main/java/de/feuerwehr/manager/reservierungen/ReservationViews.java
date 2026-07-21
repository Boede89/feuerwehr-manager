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
        boolean ownedByCurrentUser) {}

public record ReservationConflictView(
        long id,
        ReservationKind kind,
        String resourceName,
        String requesterName,
        Instant startAt,
        Instant endAt,
        ReservationStatus status) {}

public record LoeschfahrzeugWarningView(
        boolean warning,
        int totalCount,
        int reservedAfterCount,
        int remainingAfter,
        int minAvailable,
        String message) {}

public record ProcessReservationRequest(
        String action,
        String reason,
        boolean forceAvailabilityOverride,
        List<Long> conflictIds,
        List<Integer> diveraGroupIds) {}
