package de.feuerwehr.manager.berichte;

/** Mappt Anwesenheitslisten-Stammdaten auf das Einsatzbericht-Formular (gleiche UI). */
public final class AnwesenheitslisteEinsatzFormBridge {

    private AnwesenheitslisteEinsatzFormBridge() {}

    public static EinsatzberichtForm toEinsatzForm(AttendanceReport report) {
        EinsatzberichtForm form = new EinsatzberichtForm();
        if (report == null) {
            return form;
        }
        form.setIncidentNumber(report.getReportNumber());
        form.setIncidentDate(report.getEventDate());
        form.setAlarmTime(report.getStartTime());
        form.setEndTime(report.getEndTime());
        form.setStichwort(report.getTitle());
        form.setLocation(report.getLocation() != null ? report.getLocation() : "");
        form.setEinsatzkurzbericht(report.getNotes());
        form.setPersonDamageDetailsJson(PersonDamageDetailsSupport.emptyJson());
        form.setDamagePerpetratorJson(DamagePerpetratorSupport.emptyJson());
        return form;
    }

    public static void applyEinsatzForm(AttendanceReport report, EinsatzberichtForm form) {
        if (report == null || form == null) {
            return;
        }
        if (form.getIncidentDate() != null) {
            report.setEventDate(form.getIncidentDate());
        }
        report.setStartTime(form.getAlarmTime());
        report.setEndTime(form.getEndTime());
        if (form.getStichwort() != null && !form.getStichwort().isBlank()) {
            report.setTitle(form.getStichwort().trim());
        }
        report.setLocation(form.getLocation() != null ? form.getLocation().trim() : "");
        report.setNotes(form.getEinsatzkurzbericht());
        if (form.getIncidentNumber() != null && !form.getIncidentNumber().isBlank()) {
            report.setReportNumber(form.getIncidentNumber().trim());
        }
    }
}
