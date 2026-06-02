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

    public void requireCanManageUser(AppUserDetails actor, User target) {
        if (actor == null || actor.getRole().isSuperAdmin()) {
            return;
        }
        if (!actor.getRole().isUnitAdmin()) {
            throw new IllegalArgumentException("Keine Berechtigung zur Benutzerverwaltung");
        }
        Long actorUnit = actor.getUnitId();
        Long targetUnit = target.getUnit() != null ? target.getUnit().getId() : null;
        if (actorUnit == null || targetUnit == null || !actorUnit.equals(targetUnit)) {
            throw new IllegalArgumentException("Kein Zugriff auf diesen Benutzer");
        }
        if (target.getRole().isAssignableOnlyBySuperAdmin()) {
            throw new IllegalArgumentException("Keine Berechtigung zur Verwaltung dieses Kontos");
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
