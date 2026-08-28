package de.feuerwehr.manager.web.dto;

public record BugReportRequest(
        String reporterName,
        String reporterEmail,
        String area,
        String description,
        String pageUrl) {}
