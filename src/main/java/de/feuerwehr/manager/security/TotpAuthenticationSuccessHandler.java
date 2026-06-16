package de.feuerwehr.manager.security;

import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserManagementService;
import de.feuerwehr.manager.user.UserRepository;
import de.feuerwehr.manager.user.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TotpAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final UserService userService;
    private final UserManagementService userManagementService;

    {
        setDefaultTargetUrl("/");
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        AppUserDetails details = (AppUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(details.getUserId()).orElse(null);
        if (user == null || !user.isTotpEnabled() || user.getTotpSecret() == null || user.getTotpSecret().isBlank()) {
            userManagementService.registerPendingRfidFromSession(details.getUserId(), request.getSession(false), request);
            userService.recordSuccessfulLogin(details.getUserId());
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }

        HttpSession session = request.getSession();
        userManagementService.registerPendingRfidFromSession(details.getUserId(), session, request);
        session.setAttribute(TotpSessionKeys.PENDING_USER_ID, details.getUserId());
        session.setAttribute(TotpSessionKeys.PENDING_STARTED_AT, Instant.now().toEpochMilli());
        SecurityContextHolder.clearContext();
        response.sendRedirect(request.getContextPath() + "/login/totp");
    }
}
