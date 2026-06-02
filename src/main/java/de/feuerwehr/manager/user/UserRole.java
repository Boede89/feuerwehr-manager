package de.feuerwehr.manager.user;

public enum UserRole {
    ADMIN,
    USER;

    public String authority() {
        return "ROLE_" + name();
    }
}
