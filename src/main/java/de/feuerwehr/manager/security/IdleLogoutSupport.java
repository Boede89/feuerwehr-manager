package de.feuerwehr.manager.security;

import jakarta.servlet.http.HttpSession;

/**
 * Gemeinsame Idle-Logout-Logik für Session-Attribute, Filter und Tests.
 * Minuten kommen aus der Einheiten-Konfiguration; 0 = deaktiviert.
 */
public final class IdleLogoutSupport {

    public static final String MINUTES_ATTR = "fw.idleLogoutMinutes";
    public static final String LAST_ACTIVITY_ATTR = "fw.idleLastActivityMillis";

    private IdleLogoutSupport() {}

    public static boolean isExpired(Integer minutes, Long lastActivityMillis, long nowMillis) {
        if (minutes == null || minutes <= 0 || lastActivityMillis == null) {
            return false;
        }
        return nowMillis - lastActivityMillis >= minutes * 60_000L;
    }

    public static void storeConfig(HttpSession session, int minutes) {
        if (session == null) {
            return;
        }
        int normalized = Math.max(minutes, 0);
        session.setAttribute(MINUTES_ATTR, normalized);
        if (normalized <= 0) {
            session.removeAttribute(LAST_ACTIVITY_ATTR);
            return;
        }
        if (session.getAttribute(LAST_ACTIVITY_ATTR) == null) {
            session.setAttribute(LAST_ACTIVITY_ATTR, System.currentTimeMillis());
        }
    }

    public static void markActivity(HttpSession session, long nowMillis) {
        if (session == null) {
            return;
        }
        session.setAttribute(LAST_ACTIVITY_ATTR, nowMillis);
    }

    public static Integer minutesFrom(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(MINUTES_ATTR);
        if (value instanceof Integer minutes) {
            return minutes;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    public static Long lastActivityFrom(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(LAST_ACTIVITY_ATTR);
        if (value instanceof Long millis) {
            return millis;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }
}
