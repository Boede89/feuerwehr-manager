package de.feuerwehr.manager.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Konfiguration für das Atemschutz-Dashboard-Widget. */
public final class AtemschutzWidgetConfig {

    public enum Metric {
        TOTAL("total", "Gesamt", "", "all"),
        TAUGLICH("tauglich", "Tauglich", "success", "tauglich"),
        WARNUNG("warnung", "Warnung", "warn", "warnung"),
        UEBUNG_ABGELAUFEN("uebungAbgelaufen", "Übung abgelaufen", "warning", "uebung_abgelaufen"),
        NICHT_TAUGLICH("nichtTauglich", "Nicht tauglich", "danger", "nicht_tauglich");

        private final String key;
        private final String label;
        private final String cssModifier;
        private final String filter;

        Metric(String key, String label, String cssModifier, String filter) {
            this.key = key;
            this.label = label;
            this.cssModifier = cssModifier;
            this.filter = filter;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }

        public String cssModifier() {
            return cssModifier;
        }

        public String filter() {
            return filter;
        }

        public static Metric fromKey(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            for (Metric m : values()) {
                if (m.key.equals(raw) || m.name().equalsIgnoreCase(raw.trim())) {
                    return m;
                }
            }
            return null;
        }
    }

    private AtemschutzWidgetConfig() {}

    public static Map<String, Object> defaults() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("includePaused", false);
        List<Map<String, Object>> metrics = new ArrayList<>();
        for (Metric m : Metric.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", m.key());
            row.put("show", true);
            row.put("showNames", m == Metric.WARNUNG || m == Metric.NICHT_TAUGLICH || m == Metric.UEBUNG_ABGELAUFEN);
            metrics.add(row);
        }
        cfg.put("metrics", metrics);
        return cfg;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> normalize(Map<String, Object> raw) {
        Map<String, Object> defaults = defaults();
        if (raw == null || raw.isEmpty()) {
            return defaults;
        }
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("includePaused", asBool(raw.get("includePaused"), false));

        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        Object metricsRaw = raw.get("metrics");
        if (metricsRaw instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Metric metric = Metric.fromKey(String.valueOf(map.get("key")));
                if (metric == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", metric.key());
                row.put("show", asBool(map.get("show"), true));
                row.put("showNames", asBool(map.get("showNames"), false));
                byKey.put(metric.key(), row);
            }
        }
        // Legacy flat booleans
        if (byKey.isEmpty()) {
            for (Metric m : Metric.values()) {
                String showKey = "show" + Character.toUpperCase(m.key().charAt(0)) + m.key().substring(1);
                String namesKey = "names" + Character.toUpperCase(m.key().charAt(0)) + m.key().substring(1);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", m.key());
                row.put("show", asBool(raw.get(showKey), true));
                row.put("showNames", asBool(raw.get(namesKey), false));
                byKey.put(m.key(), row);
            }
        }

        List<Map<String, Object>> metrics = new ArrayList<>();
        for (Metric m : Metric.values()) {
            metrics.add(byKey.getOrDefault(m.key(), Map.of(
                    "key", m.key(),
                    "show", true,
                    "showNames", false)));
        }
        cfg.put("metrics", metrics);
        return cfg;
    }

    public static boolean includePaused(Map<String, Object> config) {
        return asBool(normalize(config).get("includePaused"), false);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> metrics(Map<String, Object> config) {
        Object raw = normalize(config).get("metrics");
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    map.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    result.add(copy);
                }
            }
            return result;
        }
        return List.of();
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
}
