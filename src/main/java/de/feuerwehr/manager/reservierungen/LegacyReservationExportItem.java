package de.feuerwehr.manager.reservierungen;

public record LegacyReservationExportItem(
        Long legacyId,
        String kind,
        String resourceName,
        String requesterName,
        String requesterEmail,
        String reason,
        String location,
        String startAt,
        String endAt,
        String status,
        String approvedAt) {}
