package de.feuerwehr.manager.security;

import de.feuerwehr.manager.dsgvo.AuditEventType;
import de.feuerwehr.manager.dsgvo.AuditService;
import de.feuerwehr.manager.settings.TestModeService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditLogoutSuccessHandler implements LogoutSuccessHandler {

    private final AuditService auditService;
    private final TestModeService testModeService;
    private final SimpleUrlLogoutSuccessHandler delegate = new SimpleUrlLogoutSuccessHandler();

    {
        delegate.setDefaultTargetUrl("/login?logout");
    }

    @Override
    public void onLogoutSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        if (authentication != null && authentication.getPrincipal() instanceof AppUserDetails details) {
            auditService.record(AuditEventType.LOGOUT, details.getUserId(), request);
        }
        if (testModeService.isEnabled()) {
            testModeService.disable();
        }
        delegate.onLogoutSuccess(request, response, authentication);
    }
}
