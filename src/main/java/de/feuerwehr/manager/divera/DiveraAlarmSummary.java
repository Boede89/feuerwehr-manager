package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiveraAlarmSummary(
        long id,
        String title,
        String text,
        String address,
        long dateEpochSeconds,
        long tsCreate,
        boolean closed) {}
