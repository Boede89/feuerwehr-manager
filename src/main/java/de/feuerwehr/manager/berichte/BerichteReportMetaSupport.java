package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.user.User;
import java.time.Instant;

/** Anzeige von Erfasser/Freigeber in Bericht-Detailansichten. */
public final class BerichteReportMetaSupport {

    public record UserRef(String username, String displayName) {}

    public record MetaView(
            String source,
            UserRef recordedBy,
            Instant recordedAt,
            UserRef releasedBy,
            Instant releasedAt) {

        static MetaView empty() {
            return new MetaView(null, null, null, null, null);
        }
    }

    private BerichteReportMetaSupport() {}

    public static MetaView forReport(Object report) {
        if (report instanceof IncidentReport incidentReport) {
            return forIncident(incidentReport);
        }
        if (report instanceof AttendanceReport attendanceReport) {
            return forAttendance(attendanceReport);
        }
        return MetaView.empty();
    }

    public static MetaView forIncident(IncidentReport report) {
        if (report == null) {
            return MetaView.empty();
        }
        String source = report.getDiveraAlarmId() != null ? "DIVERA" : null;
        UserRef recordedBy = resolveRecordedBy(
                report.getCreatedByUser(), report.getCreatedByName(), report.getReleasedByUser());
        UserRef releasedBy = isReleased(report.getStatus()) ? toUserRef(report.getReleasedByUser()) : null;
        Instant releasedAt = isReleased(report.getStatus()) ? report.getReleasedAt() : null;
        return new MetaView(source, recordedBy, report.getCreatedAt(), releasedBy, releasedAt);
    }

    public static MetaView forAttendance(AttendanceReport report) {
        if (report == null) {
            return MetaView.empty();
        }
        String source = report.getUnitTermin() != null && isSystemCreatorName(report.getCreatedByName())
                ? "Terminplan"
                : null;
        UserRef recordedBy = resolveRecordedBy(
                report.getCreatedByUser(), report.getCreatedByName(), report.getReleasedByUser());
        UserRef releasedBy = isReleased(report.getStatus()) ? toUserRef(report.getReleasedByUser()) : null;
        Instant releasedAt = isReleased(report.getStatus()) ? report.getReleasedAt() : null;
        return new MetaView(source, recordedBy, report.getCreatedAt(), releasedBy, releasedAt);
    }

    private static UserRef resolveRecordedBy(User createdByUser, String createdByName, User releasedByUser) {
        if (createdByUser != null) {
            return toUserRef(createdByUser);
        }
        if (isSystemCreatorName(createdByName) && releasedByUser != null) {
            return toUserRef(releasedByUser);
        }
        if (createdByName != null && !createdByName.isBlank()) {
            return new UserRef(null, createdByName.trim());
        }
        return null;
    }

    private static boolean isSystemCreatorName(String createdByName) {
        if (createdByName == null || createdByName.isBlank()) {
            return false;
        }
        String normalized = createdByName.trim();
        return "DIVERA".equalsIgnoreCase(normalized) || "Terminplan".equalsIgnoreCase(normalized);
    }

    private static boolean isReleased(IncidentReportStatus status) {
        return status == IncidentReportStatus.FREIGEGEBEN || status == IncidentReportStatus.ARCHIVIERT;
    }

    private static UserRef toUserRef(User user) {
        if (user == null) {
            return null;
        }
        String username = user.getUsername();
        String displayName = user.getDisplayName();
        if ((username == null || username.isBlank()) && (displayName == null || displayName.isBlank())) {
            return null;
        }
        return new UserRef(
                username != null && !username.isBlank() ? username.trim() : null,
                displayName != null && !displayName.isBlank() ? displayName.trim() : null);
    }
}
