package de.feuerwehr.manager.security;

import de.feuerwehr.manager.dsgvo.AuditEventType;
import de.feuerwehr.manager.dsgvo.AuditService;
import de.feuerwehr.manager.dsgvo.PrivacyService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
public class AuthenticationEventsListener {

    private final AuditService auditService;
    private final UserService userService;
    private final PrivacyService privacyService;

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        if (auth.getPrincipal() instanceof AppUserDetails details) {
            userService.recordSuccessfulLogin(details.getUserId());
            AuditEventType type = auth instanceof RfidAuthenticationToken
                    ? AuditEventType.RFID_LOGIN_SUCCESS
                    : AuditEventType.LOGIN_SUCCESS;
            auditService.record(type, details.getUserId(), currentRequest());
            HttpServletRequest request = currentRequest();
            userService.findById(details.getUserId()).ifPresent(user -> acceptPrivacySilently(user, request));
        }
    }

    private void acceptPrivacySilently(User user, HttpServletRequest request) {
        if (!privacyService.needsConsent(user)) {
            return;
        }
        try {
            String agent = request != null ? request.getHeader("User-Agent") : null;
            privacyService.recordConsent(user, request, agent);
        } catch (Exception ignored) {
            // Kein Blocker für Login, wenn kein Hinweis in der DB
        }
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        AuditEventType type = event.getAuthentication() instanceof RfidAuthenticationToken
                ? AuditEventType.RFID_LOGIN_FAILURE
                : AuditEventType.LOGIN_FAILURE;
        auditService.record(type, null, null, currentRequest(), "Anmeldung fehlgeschlagen");
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest();
        }
        return null;
    }
}
