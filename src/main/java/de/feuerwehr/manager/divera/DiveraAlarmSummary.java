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
        Long testRecordId,
        boolean manualAlarm,
        Long manualRecordId,
        boolean manualStarted,
        boolean exercise) {

    public DiveraAlarmSummary(
            long id, String title, String text, String address, long dateEpochSeconds, long tsCreate, boolean closed) {
        this(id, title, text, address, dateEpochSeconds, tsCreate, closed, false, null, false, null, true, false);
    }

    public DiveraAlarmSummary(
            long id,
            String title,
            String text,
            String address,
            long dateEpochSeconds,
            long tsCreate,
            boolean closed,
            boolean testAlarm,
            Long testRecordId) {
        this(id, title, text, address, dateEpochSeconds, tsCreate, closed, testAlarm, testRecordId, false, null, true, false);
    }

    public DiveraAlarmSummary(
            long id,
            String title,
            String text,
            String address,
            long dateEpochSeconds,
            long tsCreate,
            boolean closed,
            boolean testAlarm,
            Long testRecordId,
            boolean manualAlarm,
            Long manualRecordId) {
        this(
                id,
                title,
                text,
                address,
                dateEpochSeconds,
                tsCreate,
                closed,
                testAlarm,
                testRecordId,
                manualAlarm,
                manualRecordId,
                !manualAlarm,
                false);
    }

    public static DiveraAlarmSummary fromTestAlarm(
            long displayId,
            long testRecordId,
            String title,
            String text,
            String address,
            long dateEpoch,
            long tsCreate,
            boolean closed) {
        return new DiveraAlarmSummary(
                displayId, title, text, address, dateEpoch, tsCreate, closed, true, testRecordId, false, null, true, false);
    }

    public static DiveraAlarmSummary fromManualAlarm(
            long displayId,
            long manualRecordId,
            String title,
            String text,
            String address,
            long dateEpoch,
            long tsCreate,
            boolean closed,
            boolean started,
            boolean exercise) {
        return new DiveraAlarmSummary(
                displayId,
                title,
                text,
                address,
                dateEpoch,
                tsCreate,
                closed,
                false,
                null,
                true,
                manualRecordId,
                started,
                exercise);
    }

    public boolean manualDraft() {
        return manualAlarm && !manualStarted && !closed;
    }
}
