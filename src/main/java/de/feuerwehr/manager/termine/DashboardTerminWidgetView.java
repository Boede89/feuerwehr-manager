package de.feuerwehr.manager.termine;

public record DashboardTerminWidgetView(
        long terminId,
        String title,
        String day,
        String monthShort,
        String time,
        String categoryLabel,
        TermineCategory category,
        boolean today,
        boolean checkInAvailable,
        Long attendanceReportId) {

    public DashboardTerminWidgetView withCheckIn(boolean available, Long reportId) {
        return new DashboardTerminWidgetView(
                terminId,
                title,
                day,
                monthShort,
                time,
                categoryLabel,
                category,
                today,
                available,
                reportId);
    }
}
