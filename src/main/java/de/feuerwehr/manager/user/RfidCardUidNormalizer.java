package de.feuerwehr.manager.user;

/**
 * Einheitliche Speicherung von Chip-IDs unabhängig vom Lesegerät (Hex mit/ohne Trennzeichen).
 */
public final class RfidCardUidNormalizer {

    private RfidCardUidNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("[\\s:-]", "").toUpperCase();
    }

    public static boolean isValid(String normalized) {
        return normalized != null && normalized.length() >= 4 && normalized.length() <= 128
                && normalized.matches("[0-9A-F]+");
    }
}
