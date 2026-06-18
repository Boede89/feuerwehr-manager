package de.feuerwehr.manager.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Setzt das Secure-Flag des Session-Cookies pro Request: nur bei HTTPS (inkl. Reverse-Proxy
 * mit X-Forwarded-Proto). Ermöglicht Login über HTTP auf :8080 zum Debuggen, wenn
 * FEUERWEHR_SESSION_COOKIE_SECURE=true für den HTTPS-Betrieb gesetzt ist.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SessionCookieSecureFilter extends OncePerRequestFilter {

    private final boolean secureCookiesEnabled;

    public SessionCookieSecureFilter(
            @Value("${FEUERWEHR_SESSION_COOKIE_SECURE:${server.servlet.session.cookie.secure:false}}")
                    boolean secureCookiesEnabled) {
        this.secureCookiesEnabled = secureCookiesEnabled;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        SessionCookieConfig config = request.getServletContext().getSessionCookieConfig();
        if (config != null) {
            config.setSecure(secureCookiesEnabled && request.isSecure());
        }
        filterChain.doFilter(request, response);
    }
}
