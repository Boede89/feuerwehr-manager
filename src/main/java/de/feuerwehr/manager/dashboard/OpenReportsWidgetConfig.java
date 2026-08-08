package de.feuerwehr.manager.dashboard;

import java.util.LinkedHashMap;
import java.util.Map;

/** Konfiguration für das Dashboard-Widget „Offene Berichte“. */
public final class OpenReportsWidgetConfig {

    private OpenReportsWidgetConfig() {}

    public static Map<String, Object> defaults() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("showEinsatzberichte", true);
        cfg.put("showAnwesenheitslisten", true);
        cfg.put("anwesenheitOnlyUntilToday", true);
        cfg.put("limit", 15);
        cfg.put("openInEdit", true);
        return cfg;
    }

    public static Map<String, Object> normalize(Map<String, Object> raw) {
        Map<String, Object> defaults = defaults();
        if (raw == null || raw.isEmpty()) {
            return defaults;
        }
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("showEinsatzberichte", asBool(raw.get("showEinsatzberichte"), true));
        cfg.put("showAnwesenheitslisten", asBool(raw.get("showAnwesenheitslisten"), true));
        cfg.put("anwesenheitOnlyUntilToday", asBool(raw.get("anwesenheitOnlyUntilToday"), true));
        cfg.put("limit", asInt(raw.get("limit"), 15, 1, 50));
        cfg.put("openInEdit", asBool(raw.get("openInEdit"), true));
        return cfg;
    }

    public static boolean showEinsatzberichte(Map<String, Object> config) {
        return asBool(normalize(config).get("showEinsatzberichte"), true);
    }

    public static boolean showAnwesenheitslisten(Map<String, Object> config) {
        return asBool(normalize(config).get("showAnwesenheitslisten"), true);
    }

    public static boolean anwesenheitOnlyUntilToday(Map<String, Object> config) {
        return asBool(normalize(config).get("anwesenheitOnlyUntilToday"), true);
    }

    public static boolean openInEdit(Map<String, Object> config) {
        return asBool(normalize(config).get("openInEdit"), true);
    }

    public static int limit(Map<String, Object> config) {
        return asInt(normalize(config).get("limit"), 15, 1, 50);
    }

    private static boolean asBool(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s)) {
            return true;
        }
        if ("false".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s)) {
            return false;
        }
        return fallback;
    }

    private static int asInt(Object value, int fallback, int min, int max) {
        int parsed = fallback;
        if (value instanceof Number n) {
            parsed = n.intValue();
        } else if (value != null) {
            try {
                parsed = Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                parsed = fallback;
            }
        }
        return Math.max(min, Math.min(max, parsed));
    }
}
