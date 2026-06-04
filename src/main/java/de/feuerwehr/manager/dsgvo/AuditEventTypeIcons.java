package de.feuerwehr.manager.dsgvo;

/** Lucide-kompatible Inline-SVGs für die Audit-Tabelle (wie FW-Hub). */
public final class AuditEventTypeIcons {

    private static final String SVG_PREFIX =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\""
                    + " fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.75\" stroke-linecap=\"round\""
                    + " stroke-linejoin=\"round\" class=\"audit-action__icon\" aria-hidden=\"true\">";
    private static final String SVG_SUFFIX = "</svg>";

    private AuditEventTypeIcons() {}

    public static String svg(AuditEventType type) {
        if (type == null) {
            return SVG_PREFIX + PATH_CIRCLE + SVG_SUFFIX;
        }
        String path = switch (type) {
            case LOGIN_SUCCESS, RFID_LOGIN_SUCCESS -> PATH_CHECK_CIRCLE;
            case LOGIN_FAILURE, RFID_LOGIN_FAILURE -> PATH_ALERT_TRIANGLE;
            case LOGOUT -> PATH_LOG_OUT;
            case PRIVACY_CONSENT -> PATH_SHIELD;
            case USER_ANONYMIZED -> PATH_TRASH;
            case RFID_CARD_REGISTERED, USER_CREATED -> PATH_PLUS;
            case RFID_CARD_REVOKED -> PATH_X_CIRCLE;
            case USER_UPDATED -> PATH_SETTINGS;
            case PASSWORD_CHANGED -> PATH_KEY;
        };
        return SVG_PREFIX + path + SVG_SUFFIX;
    }

    private static final String PATH_CHECK_CIRCLE =
            "<path d=\"M22 11.08V12a10 10 0 1 1-5.93-9.14\"/><path d=\"m9 11 3 3L22 4\"/>";
    private static final String PATH_ALERT_TRIANGLE =
            "<path d=\"m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3\"/>"
                    + "<path d=\"M12 9v4\"/><path d=\"M12 17h.01\"/>";
    private static final String PATH_LOG_OUT =
            "<path d=\"M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4\"/>"
                    + "<polyline points=\"16 17 21 12 16 7\"/><line x1=\"21\" x2=\"9\" y1=\"12\" y2=\"12\"/>";
    private static final String PATH_SHIELD =
            "<path d=\"M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z\"/>";
    private static final String PATH_TRASH =
            "<path d=\"M3 6h18\"/><path d=\"M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6\"/>"
                    + "<path d=\"M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2\"/>";
    private static final String PATH_PLUS = "<path d=\"M5 12h14\"/><path d=\"M12 5v14\"/>";
    private static final String PATH_X_CIRCLE =
            "<circle cx=\"12\" cy=\"12\" r=\"10\"/><path d=\"m15 9-6 6\"/><path d=\"m9 9 6 6\"/>";
    private static final String PATH_SETTINGS =
            "<path d=\"M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z\"/>"
                    + "<circle cx=\"12\" cy=\"12\" r=\"3\"/>";
    private static final String PATH_KEY =
            "<path d=\"m15.5 7.5 2.3 2.3a1 1 0 0 0 1.4 0l2.1-2.1a1 1 0 0 0 0-1.4L19 4\"/>"
                    + "<path d=\"m21 2-9.6 9.6\"/><circle cx=\"7.5\" cy=\"15.5\" r=\"5.5\"/>";
    private static final String PATH_CIRCLE = "<circle cx=\"12\" cy=\"12\" r=\"10\"/>";
}
