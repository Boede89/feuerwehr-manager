package de.feuerwehr.manager.web.dto;

public record DiveraAlarmSampleListItemDto(
        long id, long alarmId, String title, String address, String capturedAt, boolean running) {}
