package de.feuerwehr.manager.web.dto;

public record BugReportResult(boolean success, String message) {

    public static BugReportResult ok(String message) {
        return new BugReportResult(true, message);
    }

    public static BugReportResult fail(String message) {
        return new BugReportResult(false, message);
    }
}
