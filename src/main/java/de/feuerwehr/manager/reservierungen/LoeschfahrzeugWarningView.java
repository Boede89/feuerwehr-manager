package de.feuerwehr.manager.reservierungen;

public record LoeschfahrzeugWarningView(
        boolean warning,
        int totalCount,
        int reservedAfterCount,
        int remainingAfter,
        int minAvailable,
        String message) {}
