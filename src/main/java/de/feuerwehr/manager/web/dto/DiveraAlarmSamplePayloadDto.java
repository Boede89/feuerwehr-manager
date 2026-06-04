package de.feuerwehr.manager.web.dto;

public record DiveraAlarmSamplePayloadDto(boolean ok, String message, String payload) {

    public static DiveraAlarmSamplePayloadDto success(String payload) {
        return new DiveraAlarmSamplePayloadDto(true, null, payload);
    }

    public static DiveraAlarmSamplePayloadDto failure(String message) {
        return new DiveraAlarmSamplePayloadDto(false, message, null);
    }
}
