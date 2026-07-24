package de.feuerwehr.manager.berichte;

/** E-Mail-Verhalten im Testmodus (Abfrage vor Aktionen mit möglichem Auto-Versand). */
public enum TestModeEmailDelivery {
    /** Keine E-Mail senden (Standard). */
    NONE,
    /** Nur an die E-Mail-Adresse des aktuellen Benutzers. */
    SELF,
    /** An die hinterlegten Empfänger der Berichte-E-Mail-Einstellungen. */
    CONFIGURED;

    public static TestModeEmailDelivery fromRequestParam(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return TestModeEmailDelivery.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
