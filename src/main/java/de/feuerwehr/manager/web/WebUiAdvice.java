package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.SecurityProperties;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class WebUiAdvice {

    private final SecurityProperties securityProperties;
    private final TestModeService testModeService;

    @ModelAttribute("isAdmin")
    public boolean isAdmin(@AuthenticationPrincipal AppUserDetails user) {
        return user != null && user.getRole() == UserRole.ADMIN;
    }

    @ModelAttribute("testModeEnabled")
    public boolean testModeEnabled() {
        return testModeService.isEnabled();
    }

    @ModelAttribute("minPasswordLength")
    public int minPasswordLength() {
        return securityProperties.minPasswordLength();
    }
}
