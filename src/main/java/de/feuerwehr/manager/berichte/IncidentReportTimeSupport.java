package de.feuerwehr.manager.berichte;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/** Alarm- und Einsatzende inkl. Einsätze über Mitternacht. */
public final class IncidentReportTimeSupport {

    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private IncidentReportTimeSupport() {}

    public static LocalDate resolveEndDate(IncidentReport report) {
        if (report == null || report.getIncidentDate() == null) {
            return report != null ? report.getEndDate() : null;
        }
        if (report.getEndDate() != null) {
            return report.getEndDate();
        }
        if (report.getEndTime() == null) {
            return report.getIncidentDate();
        }
        if (report.getAlarmTime() != null
                && (report.getEndTime().isBefore(report.getAlarmTime())
                        || report.getEndTime().equals(LocalTime.MIDNIGHT))) {
            return report.getIncidentDate().plusDays(1);
        }
        return report.getIncidentDate();
    }

    public static LocalDateTime resolveAlarmAt(IncidentReport report) {
        if (report == null || report.getIncidentDate() == null) {
            return null;
        }
        LocalTime time = report.getAlarmTime() != null ? report.getAlarmTime() : LocalTime.MIDNIGHT;
        return LocalDateTime.of(report.getIncidentDate(), time);
    }

    public static LocalDateTime resolveEndAt(IncidentReport report) {
        LocalDate endDate = resolveEndDate(report);
        if (report == null || endDate == null || report.getEndTime() == null) {
            return null;
        }
        return LocalDateTime.of(endDate, report.getEndTime());
    }

    public static Instant resolveAlarmInstant(IncidentReport report) {
        LocalDateTime at = resolveAlarmAt(report);
        return at != null ? at.atZone(ZONE).toInstant() : null;
    }

    public static Instant resolveEndInstant(IncidentReport report) {
        LocalDateTime at = resolveEndAt(report);
        return at != null ? at.atZone(ZONE).toInstant() : null;
    }

    public static long durationMinutes(IncidentReport report) {
        LocalDateTime start = resolveAlarmAt(report);
        LocalDateTime end = resolveEndAt(report);
        if (start == null || end == null) {
            return 0;
        }
        return Duration.between(start, end).toMinutes();
    }

    public static LocalDate normalizeStoredEndDate(
            LocalDate incidentDate, LocalTime alarmTime, LocalDate formEndDate, LocalTime endTime) {
        if (incidentDate == null || endTime == null) {
            return null;
        }
        LocalDate effective = formEndDate != null ? formEndDate : incidentDate;
        if (effective.isAfter(incidentDate)) {
            return effective;
        }
        if (alarmTime != null && (endTime.isBefore(alarmTime) || endTime.equals(LocalTime.MIDNIGHT))) {
            return incidentDate.plusDays(1);
        }
        return null;
    }

    public static void validateEndAfterStart(
            LocalDate incidentDate, LocalTime alarmTime, LocalDate formEndDate, LocalTime endTime) {
        if (incidentDate == null || alarmTime == null || endTime == null) {
            return;
        }
        LocalDate endDate = formEndDate != null ? formEndDate : incidentDate;
        LocalDateTime start = LocalDateTime.of(incidentDate, alarmTime);
        LocalDateTime end = LocalDateTime.of(endDate, endTime);
        if (!endDate.isAfter(incidentDate) && endTime.isBefore(alarmTime)) {
            end = end.plusDays(1);
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Das Einsatzende muss nach dem Beginn liegen.");
        }
    }
}
