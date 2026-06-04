package de.feuerwehr.manager.technik;

import java.util.Map;

public enum ChecklistInterval {
    MANUELL("manuell", "Manuell"),
    TAEGLICH("taeglich", "Täglich"),
    WOECHENTLICH("woechentlich", "Wöchentlich"),
    MONATLICH("monatlich", "Monatlich");

    private final String key;
    private final String label;

    ChecklistInterval(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public static ChecklistInterval fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return MANUELL;
        }
        String k = raw.trim().toLowerCase();
        for (ChecklistInterval i : values()) {
            if (i.key.equals(k)) {
                return i;
            }
        }
        return MANUELL;
    }

    public static String labelFor(String key) {
        return fromKey(key).label();
    }

    public static Map<String, String> options() {
        return Map.of(
                MANUELL.key, MANUELL.label,
                TAEGLICH.key, TAEGLICH.label,
                WOECHENTLICH.key, WOECHENTLICH.label,
                MONATLICH.key, MONATLICH.label);
    }
}
