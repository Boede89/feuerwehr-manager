package de.feuerwehr.manager.user;

/** Normalisierung von Anmelde-Eingaben (Benutzername oder E-Mail). */
public final class LoginIdentifierHelper {

    private LoginIdentifierHelper() {}

    public static String normalize(String login) {
        if (login == null) {
            return "";
        }
        String trimmed = login.trim();
        if (trimmed.contains("@")) {
            return trimmed.toLowerCase();
        }
        return trimmed;
    }

    public static boolean looksLikeEmail(String login) {
        return login != null && login.trim().contains("@");
    }
}
