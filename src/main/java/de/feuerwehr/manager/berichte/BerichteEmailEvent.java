package de.feuerwehr.manager.berichte;

public record BerichteEmailEvent(
        long unitId,
        BerichteEmailReportType reportType,
        long reportId,
        IncidentReportStatus status,
        Trigger trigger,
        Long vehicleId) {

    public enum Trigger {
        CREATE,
        STATUS_CHANGE
    }

    public static BerichteEmailEvent onCreate(long unitId, BerichteEmailReportType reportType, long reportId) {
        return new BerichteEmailEvent(unitId, reportType, reportId, null, Trigger.CREATE, null);
    }

    public static BerichteEmailEvent onStatusChange(
            long unitId, BerichteEmailReportType reportType, long reportId, IncidentReportStatus status) {
        return new BerichteEmailEvent(unitId, reportType, reportId, status, Trigger.STATUS_CHANGE, null);
    }

    public static BerichteEmailEvent onChecklistCreated(long unitId, long vehicleId, long checklistId) {
        return new BerichteEmailEvent(
                unitId, BerichteEmailReportType.CHECKLISTEN, checklistId, null, Trigger.CREATE, vehicleId);
    }
}
