package de.feuerwehr.manager.berichte;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnwesenheitslisteForm {

    private String reportNumber;
    private LocalDate eventDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String title;
    private String location;
    private String notes;
    private String personnelJson;

    public static AnwesenheitslisteForm fromReport(AttendanceReport report, List<AttendanceReportPersonnel> personnel) {
        AnwesenheitslisteForm form = new AnwesenheitslisteForm();
        form.setReportNumber(report.getReportNumber());
        form.setEventDate(report.getEventDate());
        form.setStartTime(report.getStartTime());
        form.setEndTime(report.getEndTime());
        form.setTitle(report.getTitle());
        form.setLocation(report.getLocation());
        form.setNotes(report.getNotes());
        return form;
    }

    public AnwesenheitslisteFormData toData(List<AnwesenheitslistePersonnelRow> personnel) {
        return new AnwesenheitslisteFormData(
                reportNumber,
                eventDate,
                startTime,
                endTime,
                title,
                location != null ? location : "",
                notes,
                personnel != null ? personnel : List.of());
    }

    public List<AnwesenheitslistePersonnelRow> emptyPersonnel() {
        return new ArrayList<>();
    }
}
