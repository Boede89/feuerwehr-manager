package de.feuerwehr.manager.security;

import de.feuerwehr.manager.dsgvo.AuditEventType;
import de.feuerwehr.manager.dsgvo.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
public class LogoutAuditListener implements ApplicationListener<LogoutSuccessEvent> {

    private final AuditService auditService;

    @Override
    public void onApplicationEvent(LogoutSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserDetails details) {
            HttpServletRequest request = null;
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servlet) {
                request = servlet.getRequest();
            }
            auditService.record(AuditEventType.LOGOUT, details.getUserId(), request);
        }
    }
}
