package de.feuerwehr.manager.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Passt das Secure-Flag von JSESSIONID pro Response an: nur bei HTTPS (inkl. Reverse-Proxy mit
 * X-Forwarded-Proto). Tomcat erlaubt kein {@code SessionCookieConfig.setSecure()} nach
 * Context-Start — deshalb Anpassung über Set-Cookie-Header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SessionCookieSecureFilter extends OncePerRequestFilter {

    private static final String SET_COOKIE = "Set-Cookie";
    private static final String SESSION_COOKIE = "JSESSIONID";

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
        if (!secureCookiesEnabled) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean secure = request.isSecure();
        filterChain.doFilter(request, new SecureSessionCookieResponseWrapper(response, secure));
    }

    private static final class SecureSessionCookieResponseWrapper extends HttpServletResponseWrapper {

        private final boolean secure;

        SecureSessionCookieResponseWrapper(HttpServletResponse response, boolean secure) {
            super(response);
            this.secure = secure;
        }

        @Override
        public void addCookie(Cookie cookie) {
            if (cookie != null && SESSION_COOKIE.equalsIgnoreCase(cookie.getName())) {
                Cookie adjusted = (Cookie) cookie.clone();
                adjusted.setSecure(secure);
                super.addCookie(adjusted);
                return;
            }
            super.addCookie(cookie);
        }

        @Override
        public void addHeader(String name, String value) {
            if (SET_COOKIE.equalsIgnoreCase(name)) {
                super.addHeader(name, adjustSessionCookie(value, secure));
                return;
            }
            super.addHeader(name, value);
        }

        @Override
        public void setHeader(String name, String value) {
            if (SET_COOKIE.equalsIgnoreCase(name)) {
                super.setHeader(name, adjustSessionCookie(value, secure));
                return;
            }
            super.setHeader(name, value);
        }

        @Override
        public void setDateHeader(String name, long date) {
            super.setDateHeader(name, date);
        }

        @Override
        public void addDateHeader(String name, long date) {
            super.addDateHeader(name, date);
        }
    }

    static String adjustSessionCookie(String headerValue, boolean secure) {
        if (headerValue == null || headerValue.isBlank()) {
            return headerValue;
        }
        if (!headerValue.regionMatches(true, 0, SESSION_COOKIE + "=", 0, SESSION_COOKIE.length() + 1)) {
            return headerValue;
        }
        if (secure) {
            return hasSecureAttribute(headerValue) ? headerValue : headerValue + "; Secure";
        }
        return stripSecureAttribute(headerValue);
    }

    static boolean hasSecureAttribute(String cookie) {
        return cookie.matches("(?i).*;\\s*Secure(?:\\s*;|$)");
    }

    static String stripSecureAttribute(String cookie) {
        return cookie.replaceAll("(?i);\\s*Secure", "");
    }
}
