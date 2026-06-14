package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.user.User;

public final class MaengelberichtAccess {

    private MaengelberichtAccess() {}

    public static boolean canEdit(DefectReport report, AppUserDetails actor) {
        if (actor == null) {
            return false;
        }
        if (actor.getRole().isAdminLevel()) {
            return true;
        }
        User creator = report.getCreatedByUser();
        return creator != null && creator.getId().equals(actor.getUserId());
    }

    public static boolean canDelete(DefectReport report, AppUserDetails actor) {
        return canEdit(report, actor);
    }
}
