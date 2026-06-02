package de.feuerwehr.manager.unit;

import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Merkt sich die zuletzt gewählte Einheit pro Browser-Sitzung. */
@Service
public class UnitSelectionService {

    static final String SESSION_KEY = "selectedUnitId";

    public void remember(long unitId) {
        HttpSession session = currentSession(true);
        if (session != null) {
            session.setAttribute(SESSION_KEY, unitId);
        }
    }

    public Optional<Long> getRemembered() {
        HttpSession session = currentSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object value = session.getAttribute(SESSION_KEY);
        if (value instanceof Long id) {
            return Optional.of(id);
        }
        if (value instanceof Integer id) {
            return Optional.of(id.longValue());
        }
        return Optional.empty();
    }

    public void clear() {
        HttpSession session = currentSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_KEY);
        }
    }

    private static HttpSession currentSession(boolean create) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest().getSession(create);
        }
        return null;
    }
}
