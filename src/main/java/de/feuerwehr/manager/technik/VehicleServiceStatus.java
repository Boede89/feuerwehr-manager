package de.feuerwehr.manager.technik;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fahrzeug-Status / Einsatzbereitschaft (analog FW-Hub). */
public final class VehicleServiceStatus {

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("aktiv", "Einsatzbereit");
        LABELS.put("ausser_dienst", "Außer Dienst");
        LABELS.put("wartung", "In Wartung");
    }

    private VehicleServiceStatus() {}

    public static Map<String, String> labels() {
        return LABELS;
    }

    public static String labelFor(String key) {
        if (key == null || key.isBlank()) {
            return LABELS.get("aktiv");
        }
        return LABELS.getOrDefault(key, key);
    }

    public static String normalize(String status) {
        if (status == null || status.isBlank()) {
            return "aktiv";
        }
        String s = status.trim().toLowerCase();
        return LABELS.containsKey(s) ? s : "aktiv";
    }

    public static boolean isActive(String serviceStatus) {
        return "aktiv".equals(normalize(serviceStatus));
    }

    /** CSS-Modifikator für Status-Badges (analog FW-Hub). */
    public static String cssModifier(String serviceStatus) {
        return normalize(serviceStatus);
    }
}
