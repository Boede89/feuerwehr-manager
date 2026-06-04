package de.feuerwehr.manager.dsgvo;

public final class AuditEventTypeLabels {

    private AuditEventTypeLabels() {}

    public static String label(AuditEventType type) {
        if (type == null) {
            return "—";
        }
        return switch (type) {
            case LOGIN_SUCCESS -> "Login erfolgreich";
            case LOGIN_FAILURE -> "Login fehlgeschlagen";
            case LOGOUT -> "Abmeldung";
            case RFID_LOGIN_SUCCESS -> "RFID-Login erfolgreich";
            case RFID_LOGIN_FAILURE -> "RFID-Login fehlgeschlagen";
            case PRIVACY_CONSENT -> "Datenschutz-Einwilligung";
            case USER_ANONYMIZED -> "Benutzer gelöscht";
            case RFID_CARD_REGISTERED -> "RFID-Karte registriert";
            case RFID_CARD_REVOKED -> "RFID-Karte widerrufen";
            case USER_CREATED -> "Benutzer angelegt";
            case USER_UPDATED -> "Benutzer geändert";
            case PASSWORD_CHANGED -> "Passwort geändert";
        };
    }

    /** Anzeige in der Aktion-Spalte (inkl. gelöschter Benutzername). */
    public static String actionLabel(AuditEventType type, String detail) {
        String base = label(type);
        if (type == AuditEventType.USER_ANONYMIZED && detail != null && !detail.isBlank()) {
            return base + ": " + detail.trim();
        }
        return base;
    }
}
