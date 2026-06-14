package de.feuerwehr.manager.berichte;

import java.time.LocalDate;

public record GeraetewartmitteilungListItemView(
        long id,
        LocalDate eventDate,
        String typKey,
        String typLabel,
        String eventArtLabel,
        String readinessKey,
        String readinessLabel,
        String leaderDisplay,
        int vehicleCount,
        Long createdByUserId) {}
