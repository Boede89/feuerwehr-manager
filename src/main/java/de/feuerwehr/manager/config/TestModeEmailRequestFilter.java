package de.feuerwehr.manager.config;

import de.feuerwehr.manager.berichte.TestModeEmailContext;
import de.feuerwehr.manager.berichte.TestModeEmailDelivery;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Übernimmt {@code testModeEmailDelivery} aus dem Request und stellt Actor-E-Mail
 * für den Testmodus-Versand bereit.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
@RequiredArgsConstructor
public class TestModeEmailRequestFilter extends OncePerRequestFilter {

    private final TestModeService testModeService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            if (testModeService.isEnabled() && isRelevantMethod(request.getMethod())) {
                TestModeEmailDelivery delivery =
                        TestModeEmailDelivery.fromRequestParam(request.getParameter("testModeEmailDelivery"));
                TestModeEmailContext.set(delivery, resolveActorEmail());
            }
            filterChain.doFilter(request, response);
        } finally {
            TestModeEmailContext.clear();
        }
    }

    private static boolean isRelevantMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    private String resolveActorEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserDetails actor)) {
            return null;
        }
        return userRepository
                .findById(actor.getUserId())
                .map(User::getLoginEmail)
                .filter(email -> email != null && !email.isBlank())
                .map(String::trim)
                .orElse(null);
    }
}
