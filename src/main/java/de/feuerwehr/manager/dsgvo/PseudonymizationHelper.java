package de.feuerwehr.manager.dsgvo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.util.StringUtils;

/**
 * Pseudonymisierung für Audit-Logs (IP, User-Agent) – keine Klartext-Speicherung in der DB.
 */
public final class PseudonymizationHelper {

    private PseudonymizationHelper() {}

    public static String hashValue(String salt, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String material = salt + "|" + value.trim();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }
}
