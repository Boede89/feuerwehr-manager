package de.feuerwehr.manager.security;

import de.feuerwehr.manager.dsgvo.AuditEventType;
import de.feuerwehr.manager.dsgvo.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Beendet die Web-Sitzung, wenn die konfigurierte Idle-Zeit überschritten ist.
 * Gilt nicht für die Einsatz-App-API ({@code /api/}).
 */
@RequiredArgsConstructor
public class IdleLogoutFilter extends OncePerRequestFilter {

    private final TestModeLogoutHandler testModeLogoutHandler;
    private final AuditService auditService;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathWithinApplication(request);
        if ("/logout".equals(path)
                || path.equals("/login")
                || path.startsWith("/login/")
                || path.startsWith("/api/")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/actuator/")
                || path.startsWith("/reservieren")
                || path.startsWith("/check-in")
                || path.startsWith("/datenschutz")
                || path.startsWith("/privacy/")
                || "/error".equals(path)
                || "/favicon.ico".equals(path)) {
            return true;
        }
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null) {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }
        Integer minutes = IdleLogoutSupport.minutesFrom(session);
        if (minutes == null || minutes <= 0) {
            filterChain.doFilter(request, response);
            return;
        }
        long now = System.currentTimeMillis();
        if (IdleLogoutSupport.isExpired(minutes, IdleLogoutSupport.lastActivityFrom(session), now)) {
            expireSession(request, response, authentication);
            return;
        }
        IdleLogoutSupport.markActivity(session, now);
        filterChain.doFilter(request, response);
    }

    private void expireSession(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        if (authentication != null && authentication.getPrincipal() instanceof AppUserDetails details) {
            auditService.record(AuditEventType.LOGOUT, details.getUserId(), request);
        }
        testModeLogoutHandler.logout(request, response, authentication);
        logoutHandler.logout(request, response, authentication);
        if (wantsJson(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"expired\":true}");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/login?expired=1");
    }

    private static boolean wantsJson(HttpServletRequest request) {
        String path = pathWithinApplication(request);
        if ("/session/keepalive".equals(path)) {
            return true;
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return true;
        }
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        return uri == null || uri.isEmpty() ? "/" : uri;
    }
}
