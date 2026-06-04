package de.feuerwehr.manager.web.dto;

public record ActionResultDto(boolean ok, String message, Integer imported, Integer skipped) {

    public static ActionResultDto success(String message) {
        return new ActionResultDto(true, message, null, null);
    }

    public static ActionResultDto success(String message, int imported, int skipped) {
        return new ActionResultDto(true, message, imported, skipped);
    }

    public static ActionResultDto failure(String message) {
        return new ActionResultDto(false, message, null, null);
    }
}
