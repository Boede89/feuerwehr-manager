package de.feuerwehr.manager.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Leitet Benutzer mit zugesandtem Initialpasswort zur Pflicht-Passwortänderung.
 */
@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AppUserDetails details
                && details.isMustChangePassword()) {
            String path = request.getRequestURI();
            String context = request.getContextPath() == null ? "" : request.getContextPath();
            if (context.length() > 0 && path.startsWith(context)) {
                path = path.substring(context.length());
            }
            if (!isAllowedWhileMustChange(path)) {
                if (path.startsWith("/api/")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"Passwortänderung erforderlich\"}");
                    return;
                }
                response.sendRedirect(context + "/profile/password-required");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isAllowedWhileMustChange(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return false;
        }
        return path.equals("/profile/password-required")
                || path.equals("/profile/password")
                || path.startsWith("/logout")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/favicon")
                || path.startsWith("/actuator/")
                || path.startsWith("/error")
                || path.startsWith("/login");
    }
}
