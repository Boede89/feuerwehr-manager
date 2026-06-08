package de.feuerwehr.manager.web.dto;

public record DiveraRawApiDto(
        boolean ok,
        String message,
        String apiBaseUrl,
        String alarmsEndpoint,
        String usersEndpoint,
        String alarmsJson,
        String usersJson,
        Integer alarmsHttpStatus,
        Integer usersHttpStatus) {

    public static DiveraRawApiDto failure(String message) {
        return new DiveraRawApiDto(false, message, null, null, null, null, null, null, null);
    }
}
