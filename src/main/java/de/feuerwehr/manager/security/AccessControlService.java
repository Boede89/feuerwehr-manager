package de.feuerwehr.manager.security;

import de.feuerwehr.manager.personal.Person;
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

    public void requireAdminLevel(AppUserDetails actor) {
        if (actor == null || !actor.getRole().isAdminLevel()) {
            throw new IllegalArgumentException("Nur Einheits- oder Superadmins dürfen diese Aktion ausführen.");
        }
    }

    public void requireSuperAdmin(AppUserDetails actor) {
        if (actor == null || !actor.getRole().isSuperAdmin()) {
            throw new IllegalArgumentException("Nur Superadmins dürfen diese Aktion ausführen.");
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

    public boolean canDeletePerson(AppUserDetails actor, Person person) {
        if (actor == null || person == null || !actor.getRole().isAdminLevel()) {
            return false;
        }
        User linked = person.getUser();
        if (linked != null && linked.getAnonymizedAt() == null) {
            if (actor.getUserId().equals(linked.getId())) {
                return false;
            }
            return canManageUser(actor, linked);
        }
        if (actor.getRole().isSuperAdmin()) {
            return true;
        }
        Long actorUnit = actor.getUnitId();
        Long personUnit = person.getUnit() != null ? person.getUnit().getId() : null;
        return actorUnit != null && actorUnit.equals(personUnit);
    }

    public void requireCanDeletePerson(AppUserDetails actor, Person person) {
        if (canDeletePerson(actor, person)) {
            return;
        }
        User linked = person != null ? person.getUser() : null;
        if (linked != null
                && linked.getAnonymizedAt() == null
                && linked.getRole() == UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("Superadmin-Personen können nur vom Superadmin gelöscht werden.");
        }
        if (linked != null
                && linked.getAnonymizedAt() == null
                && linked.getRole() == UserRole.UNIT_ADMIN) {
            throw new IllegalArgumentException(
                    "Einheitsadmin-Personen können nur vom Einheitsadmin der Einheit oder vom Superadmin gelöscht werden.");
        }
        if (actor != null && person != null && person.getUser() != null && actor.getUserId().equals(person.getUser().getId())) {
            throw new IllegalArgumentException("Sie können sich nicht selbst löschen.");
        }
        throw new IllegalArgumentException("Kein Zugriff zum Löschen dieser Person.");
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
