package de.feuerwehr.manager.leitstellen;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

public enum LeitstellenMailKind {
    DEPESCHE("Depeche"),
    ABSCHLUSS("Abschlussbericht");

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm").withZone(ZONE);

    private final String baseName;

    LeitstellenMailKind(String baseName) {
        this.baseName = baseName;
    }

    public String baseName() {
        return baseName;
    }

    /** Legacy-Name ohne Zeitstempel (für Abwärtskompatibilität bei der Erkennung). */
    public String storedFilename() {
        return baseName + ".pdf";
    }

    /** Anzeigename mit E-Mail-Empfangszeit, z. B. Depeche_17-07-2026_20-15.pdf */
    public String storedFilename(Instant receivedAt) {
        Instant ts = receivedAt != null ? receivedAt : Instant.now();
        return baseName + "_" + FILE_TS.format(ts) + ".pdf";
    }

    public boolean matchesFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String name = filename.trim();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String lower = stem.toLowerCase(Locale.ROOT);
        String base = baseName.toLowerCase(Locale.ROOT);
        return lower.equals(base) || lower.startsWith(base + "_");
    }

    public static Optional<LeitstellenMailKind> fromFilename(String filename) {
        for (LeitstellenMailKind kind : values()) {
            if (kind.matchesFilename(filename)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
