package de.feuerwehr.manager.user;

import java.util.EnumSet;
import java.util.Set;

public enum UserRole {
    SUPER_ADMIN,
    UNIT_ADMIN,
    USER;

    public String authority() {
        return "ROLE_" + name();
    }

    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }

    public boolean isUnitAdmin() {
        return this == UNIT_ADMIN;
    }

    public boolean isAdminLevel() {
        return this == SUPER_ADMIN || this == UNIT_ADMIN;
    }

    /** Nur Superadmin darf Einheitsadmin oder Superadmin vergeben. */
    public boolean isAssignableOnlyBySuperAdmin() {
        return this == SUPER_ADMIN || this == UNIT_ADMIN;
    }

    public boolean requiresUnit() {
        return this == UNIT_ADMIN || this == USER;
    }

    public static Set<UserRole> assignableBy(UserRole actorRole) {
        if (actorRole == SUPER_ADMIN) {
            return EnumSet.allOf(UserRole.class);
        }
        if (actorRole == UNIT_ADMIN) {
            return EnumSet.of(USER, UNIT_ADMIN);
        }
        return EnumSet.noneOf(UserRole.class);
    }
}
