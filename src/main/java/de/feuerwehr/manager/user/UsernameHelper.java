package de.feuerwehr.manager.user;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Regeln für Benutzernamen (Login). */
public final class UsernameHelper {

    private UsernameHelper() {}

    /**
     * Vorschlag aus Person: erster Buchstabe des Vornamens + Punkt + Nachname (z. B. m.mustermann).
     */
    public static String suggestFromPersonName(String firstName, String lastName) {
        List<String> candidates = suggestUsernameCandidates(firstName, lastName);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Vor- und Nachname sind für den Benutzernamen erforderlich");
        }
        return candidates.get(0);
    }

    /**
     * Kandidaten bei Namenskollision, vom kurzen zum eindeutigeren:
     * {@code m.mustermann} → {@code ma.mustermann} → {@code max.mustermann}.
     */
    public static List<String> suggestUsernameCandidates(String firstName, String lastName) {
        String first = normalizeNamePart(firstName);
        String last = normalizeNamePart(lastName);
        if (first.isEmpty() || last.isEmpty()) {
            throw new IllegalArgumentException("Vor- und Nachname sind für den Benutzernamen erforderlich");
        }
        Set<String> ordered = new LinkedHashSet<>();
        ordered.add(sanitizeUsername(first.charAt(0) + "." + last));
        if (first.length() >= 2) {
            ordered.add(sanitizeUsername(first.substring(0, 2) + "." + last));
        }
        if (first.length() >= 3) {
            ordered.add(sanitizeUsername(first + "." + last));
        }
        return new ArrayList<>(ordered);
    }

    public static void validate(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Benutzername fehlt");
        }
        String trimmed = username.trim();
        if (trimmed.length() < 3 || trimmed.length() > 64) {
            throw new IllegalArgumentException("Benutzername: 3–64 Zeichen");
        }
        if (!trimmed.matches("[a-zA-Z0-9._-]+")) {
            throw new IllegalArgumentException("Benutzername: nur Buchstaben, Ziffern, Punkt, Unterstrich, Bindestrich");
        }
    }

    public static String sanitizeUsername(String raw) {
        String sanitized = raw.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "");
        sanitized = sanitized.replaceAll("_+", "_").replaceAll("\\.+", ".").replaceAll("^\\.|\\.$", "");
        if (sanitized.length() < 3) {
            sanitized = ("user." + sanitized).replaceAll("^\\.", "");
        }
        return truncate(sanitized);
    }

    private static String normalizeNamePart(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String normalized = name.trim().toLowerCase();
        normalized = normalized
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");
        return normalized.replaceAll("[^a-z]", "");
    }

    static String truncate(String username) {
        if (username.length() <= 64) {
            return username;
        }
        return username.substring(0, 64);
    }
}
