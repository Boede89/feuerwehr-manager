package de.feuerwehr.manager.user;

public final class UserRoleLabels {

    private UserRoleLabels() {}

    public static String label(UserRole role) {
        if (role == null) {
            return "—";
        }
        return switch (role) {
            case SUPER_ADMIN -> "Superadmin";
            case UNIT_ADMIN -> "Einheitsadmin";
            case USER -> "Benutzer";
        };
    }
}
