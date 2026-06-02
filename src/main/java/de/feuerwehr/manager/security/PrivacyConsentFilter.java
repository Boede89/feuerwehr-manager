package de.feuerwehr.manager.security;

import de.feuerwehr.manager.dsgvo.PrivacyService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class PrivacyConsentFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "/login",
            "/logout",
            "/privacy",
            "/css/",
            "/actuator/health",
            "/error",
            "/api/v1/auth/rfid");

    private final PrivacyService privacyService;
    private final UserService userService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isAllowedWithoutConsent(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null
                && auth.isAuthenticated()
                && auth.getPrincipal() instanceof AppUserDetails details) {
            User user = userService.findById(details.getUserId()).orElse(null);
            if (user != null && privacyService.needsConsent(user)) {
                response.sendRedirect(request.getContextPath() + "/privacy/accept");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowedWithoutConsent(HttpServletRequest request) {
        String path = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }
        if (path.isEmpty()) {
            path = "/";
        }
        for (String prefix : ALLOWED_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
