package de.feuerwehr.manager.security;

import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserService userService;
    private final SecurityProperties securityProperties;

    @Override
    public void run(ApplicationArguments args) {
        String username = securityProperties.bootstrapAdminUsername();
        String password = securityProperties.bootstrapAdminPassword();
        String displayName = securityProperties.bootstrapAdminDisplayName();

        Optional<User> existing = userService.findByUsername(username);

        if (securityProperties.bootstrapAdminResetPassword() && existing.isPresent()) {
            userService.resetPassword(existing.get().getId(), password);
            log.warn(
                    "Administrator-Passwort zurückgesetzt (Benutzer: {}). "
                            + "FEUERWEHR_BOOTSTRAP_ADMIN_RESET_PASSWORD wieder deaktivieren.",
                    username);
            return;
        }

        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
                userService.resetPassword(user.getId(), password);
                log.warn("Administrator ohne Passwort – Initialpasswort gesetzt (Benutzer: {}).", username);
            }
            return;
        }

        userService.ensureBootstrapAdmin(username, password, displayName);
        log.warn(
                "Erst-Administrator angelegt (Benutzername: {}). Passwort über "
                        + "FEUERWEHR_BOOTSTRAP_ADMIN_PASSWORD festlegen und in Produktion ändern.",
                username);
    }
}
