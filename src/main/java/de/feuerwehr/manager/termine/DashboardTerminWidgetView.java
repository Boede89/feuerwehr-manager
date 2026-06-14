package de.feuerwehr.manager.termine;

public record DashboardTerminWidgetView(
        String title,
        String day,
        String monthShort,
        String time,
        String categoryLabel,
        TermineCategory category) {}
