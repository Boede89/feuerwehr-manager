package de.feuerwehr.manager.security;

import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRole;
import org.springframework.stereotype.Service;

@Service
public class AccessControlService {

    public void requireUnitAccess(AppUserDetails actor, long unitId) {
        if (actor == null) {
            return;
        }
        if (actor.getRole().isSuperAdmin()) {
            return;
        }
        Long assigned = actor.getUnitId();
        if (assigned == null || !assigned.equals(unitId)) {
            throw new IllegalArgumentException("Kein Zugriff auf diese Einheit");
        }
    }

    public boolean canManageUser(AppUserDetails actor, User target) {
        if (actor == null || target == null) {
            return false;
        }
        if (target.getRole() == UserRole.SUPER_ADMIN) {
            return actor.getRole().isSuperAdmin();
        }
        if (actor.getRole().isSuperAdmin()) {
            return true;
        }
        if (!actor.getRole().isUnitAdmin()) {
            return false;
        }
        Long actorUnit = actor.getUnitId();
        Long targetUnit = target.getUnit() != null ? target.getUnit().getId() : null;
        return actorUnit != null && actorUnit.equals(targetUnit);
    }

    public void requireCanManageUser(AppUserDetails actor, User target) {
        if (!canManageUser(actor, target)) {
            if (target != null && target.getRole() == UserRole.SUPER_ADMIN) {
                throw new IllegalArgumentException("Superadmin-Konten können nur vom Superadmin verwaltet werden.");
            }
            throw new IllegalArgumentException("Kein Zugriff auf diesen Benutzer");
        }
    }

    public void requireCanAssignRole(AppUserDetails actor, UserRole newRole) {
        if (newRole == null) {
            throw new IllegalArgumentException("Rolle fehlt");
        }
        if (actor == null) {
            throw new IllegalArgumentException("Nicht angemeldet");
        }
        if (!UserRole.assignableBy(actor.getRole()).contains(newRole)) {
            throw new IllegalArgumentException("Sie dürfen diese Rolle nicht vergeben");
        }
    }
}
