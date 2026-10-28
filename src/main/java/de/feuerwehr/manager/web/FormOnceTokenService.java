package de.feuerwehr.manager.web;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Einmal-Token gegen doppelte Formular-Speicherungen (z. B. Mehrfachklick während E-Mail-Versand).
 */
@Service
public class FormOnceTokenService {

    private static final String SESSION_ATTR = "fw.form.once.tokens";

    public String issue(HttpSession session, String key) {
        String token = newToken();
        tokenMap(session).put(normalize(key), token);
        return token;
    }

    public boolean consume(HttpSession session, String key, String submitted) {
        if (session == null || submitted == null || submitted.isBlank()) {
            return false;
        }
        Map<String, String> map = tokenMap(session);
        String normalized = normalize(key);
        String expected = map.get(normalized);
        if (expected == null || !expected.equals(submitted)) {
            return false;
        }
        map.put(normalized, newToken());
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> tokenMap(HttpSession session) {
        Object existing = session.getAttribute(SESSION_ATTR);
        if (existing instanceof Map<?, ?> map) {
            return (Map<String, String>) map;
        }
        Map<String, String> created = new HashMap<>();
        session.setAttribute(SESSION_ATTR, created);
        return created;
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }

    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
