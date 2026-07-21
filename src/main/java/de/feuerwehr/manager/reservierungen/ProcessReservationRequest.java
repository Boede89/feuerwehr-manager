package de.feuerwehr.manager.reservierungen;

import java.util.List;

public record ProcessReservationRequest(
        String action,
        String reason,
        boolean forceAvailabilityOverride,
        List<Long> conflictIds,
        List<Integer> diveraGroupIds) {}
