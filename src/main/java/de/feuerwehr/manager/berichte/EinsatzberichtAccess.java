package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.user.User;

/** Zugriffsregeln für Einsatzberichte (angelehnt an FW-Hub). */
public final class EinsatzberichtAccess {

    private EinsatzberichtAccess() {}

    public static boolean canEdit(IncidentReport report, AppUserDetails actor, boolean canApprove) {
        if (actor == null) {
            return canEdit(report, null, false, canApprove);
        }
        return canEdit(report, actor.getUserId(), actor.getRole().isAdminLevel(), canApprove);
    }

    public static boolean canEdit(IncidentReport report, Long userId, boolean adminLevel, boolean canApprove) {
        if (adminLevel) {
            return true;
        }
        if (report.getStatus() != IncidentReportStatus.ENTWURF) {
            return false;
        }
        if (canApprove) {
            return true;
        }
        return isCreator(report, userId);
    }

    /** Endgültiges Löschen nur für Superadmin/Einheitsadmin und nur im Archiv. */
    public static boolean canDelete(IncidentReport report, AppUserDetails actor, boolean canApprove) {
        if (actor == null) {
            return false;
        }
        return canDelete(report, actor.getRole().isAdminLevel());
    }

    public static boolean canDelete(IncidentReport report, boolean adminLevel) {
        return adminLevel && report.getStatus() == IncidentReportStatus.ARCHIVIERT;
    }

    public static boolean canRelease(IncidentReport report, boolean canApprove, AppUserDetails actor) {
        return report.getStatus() == IncidentReportStatus.ENTWURF
                && (canApprove || (actor != null && actor.getRole().isAdminLevel()));
    }

    /**
     * Ins Archiv verschieben: Entwürfe (Ersteller/Freigeber/Admin) und freigegebene Berichte
     * (Freigeber/Admin).
     */
    public static boolean canArchive(IncidentReport report, boolean canApprove, AppUserDetails actor) {
        if (report.getStatus() == IncidentReportStatus.ARCHIVIERT) {
            return false;
        }
        if (actor != null && actor.getRole().isAdminLevel()) {
            return true;
        }
        if (report.getStatus() == IncidentReportStatus.FREIGEGEBEN) {
            return canApprove;
        }
        if (report.getStatus() == IncidentReportStatus.ENTWURF) {
            return canApprove || isCreator(report, actor != null ? actor.getUserId() : null);
        }
        return false;
    }

    /** Änderungshistorie nur nach Freigabe (inkl. archivierter Berichte). */
    public static boolean showChangeHistory(IncidentReport report) {
        if (report == null) {
            return false;
        }
        return report.getStatus() == IncidentReportStatus.FREIGEGEBEN
                || report.getStatus() == IncidentReportStatus.ARCHIVIERT;
    }

    private static boolean isCreator(IncidentReport report, Long userId) {
        if (userId == null) {
            return false;
        }
        User creator = report.getCreatedByUser();
        return creator != null && creator.getId().equals(userId);
    }
}
