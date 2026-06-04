package de.feuerwehr.manager.technik;

import java.util.LinkedHashMap;
import java.util.Map;

/** Einsatzmitteltypen (analog FW-Hub). */
public final class VehicleTypes {

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("lkw", "LKW");
        LABELS.put("loeschfahrzeug", "Löschfahrzeug");
        LABELS.put("drehleiter", "Drehleiter");
        LABELS.put("pkw", "PKW");
        LABELS.put("anhaenger", "Anhänger");
        LABELS.put("fuehrungsfahrzeug", "Führungsfahrzeug");
        LABELS.put("sonstiges", "Sonstiges");
    }

    private VehicleTypes() {}

    public static Map<String, String> labels() {
        return LABELS;
    }

    public static String labelFor(String key) {
        if (key == null || key.isBlank()) {
            return "—";
        }
        return LABELS.getOrDefault(key, key);
    }

    public static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return "lkw";
        }
        String k = key.trim().toLowerCase();
        return LABELS.containsKey(k) ? k : "sonstiges";
    }
}
