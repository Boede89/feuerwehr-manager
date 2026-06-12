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
        form.setPostalCode(report.getPostalCode());
        form.setDistrict(report.getDistrict());
        form.setStreet(report.getStreet());
        form.setHouseNumber(report.getHouseNumber());
        form.setObjekt(report.getObjekt());
        form.setIncidentCommander(report.getInstructorResponsible());
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
        report.setPostalCode(trimOrNull(form.getPostalCode()));
        report.setDistrict(trimOrNull(form.getDistrict()));
        report.setStreet(trimOrNull(form.getStreet()));
        report.setHouseNumber(trimOrNull(form.getHouseNumber()));
        report.setObjekt(trimOrNull(form.getObjekt()));
        String instructor = form.getIncidentCommander();
        report.setInstructorResponsible(instructor != null && !instructor.isBlank() ? instructor.trim() : null);
        report.setNotes(form.getEinsatzkurzbericht());
        if (form.getIncidentNumber() != null && !form.getIncidentNumber().isBlank()) {
            report.setReportNumber(form.getIncidentNumber().trim());
        }
    }

    private static String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
