package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.user.User;

/** Zugriffsregeln für Anwesenheitslisten (angelehnt an Einsatzberichte). */
public final class AnwesenheitslisteAccess {

    private AnwesenheitslisteAccess() {}

    public static boolean canEdit(AttendanceReport report, AppUserDetails actor, boolean canApprove) {
        if (actor == null) {
            return canEdit(report, null, false, canApprove);
        }
        return canEdit(report, actor.getUserId(), actor.getRole().isAdminLevel(), canApprove);
    }

    public static boolean canEdit(AttendanceReport report, Long userId, boolean adminLevel, boolean canApprove) {
        if (adminLevel) {
            return true;
        }
        if (report.getStatus() != IncidentReportStatus.ENTWURF) {
            return false;
        }
        if (canApprove) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        User creator = report.getCreatedByUser();
        return creator != null && creator.getId().equals(userId);
    }

    public static boolean canDelete(AttendanceReport report, AppUserDetails actor, boolean canApprove) {
        if (actor == null) {
            return canDelete(report, null, false, canApprove);
        }
        return canDelete(report, actor.getUserId(), actor.getRole().isAdminLevel(), canApprove);
    }

    public static boolean canDelete(AttendanceReport report, Long userId, boolean adminLevel, boolean canApprove) {
        if (adminLevel) {
            return true;
        }
        if (canApprove
                && (report.getStatus() == IncidentReportStatus.FREIGEGEBEN
                        || report.getStatus() == IncidentReportStatus.ARCHIVIERT)) {
            return true;
        }
        if (report.getStatus() != IncidentReportStatus.ENTWURF) {
            return false;
        }
        if (canApprove) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        User creator = report.getCreatedByUser();
        return creator != null && creator.getId().equals(userId);
    }

    public static boolean canRelease(AttendanceReport report, boolean canApprove, AppUserDetails actor) {
        return report.getStatus() == IncidentReportStatus.ENTWURF
                && (canApprove || (actor != null && actor.getRole().isAdminLevel()));
    }

    public static boolean canArchive(AttendanceReport report, boolean canApprove, AppUserDetails actor) {
        return report.getStatus() == IncidentReportStatus.FREIGEGEBEN
                && (canApprove || (actor != null && actor.getRole().isAdminLevel()));
    }
}
