package de.feuerwehr.manager.berichte;

public record PublicCheckInOption(
        long unitId,
        String unitName,
        long terminId,
        String theme,
        String categoryLabel,
        String startTimeLabel) {}
