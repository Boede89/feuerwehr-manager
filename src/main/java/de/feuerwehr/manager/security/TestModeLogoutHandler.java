package de.feuerwehr.manager.security;

import de.feuerwehr.manager.settings.TestModeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/** Beendet den globalen Testmodus beim Abmelden (inkl. Löschen der Testdaten). */
@Component
@RequiredArgsConstructor
public class TestModeLogoutHandler implements LogoutHandler {

    private final TestModeService testModeService;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (testModeService.isEnabled()) {
            testModeService.disable();
        }
    }
}
