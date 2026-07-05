package de.feuerwehr.manager.einsatzapp;

import java.time.Instant;

/** Zusammenfassung eines in der Einsatz-App angemeldeten Benutzers (ein oder mehrere Geräte). */
public record EinsatzappRegisteredUserRow(
        long userId,
        String username,
        String displayName,
        int deviceCount,
        String deviceSummary,
        Instant firstRegisteredAt,
        Instant lastSeenAt,
        boolean pushEligible) {}
