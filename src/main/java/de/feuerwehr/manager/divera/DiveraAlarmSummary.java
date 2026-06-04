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
        boolean closed,
        boolean testAlarm,
        Long testRecordId) {

    public DiveraAlarmSummary(
            long id, String title, String text, String address, long dateEpochSeconds, long tsCreate, boolean closed) {
        this(id, title, text, address, dateEpochSeconds, tsCreate, closed, false, null);
    }

    public static DiveraAlarmSummary fromTestAlarm(
            long displayId, long testRecordId, String title, String text, String address, long dateEpoch, long tsCreate, boolean closed) {
        return new DiveraAlarmSummary(displayId, title, text, address, dateEpoch, tsCreate, closed, true, testRecordId);
    }
}
