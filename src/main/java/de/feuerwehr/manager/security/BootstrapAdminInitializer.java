package de.feuerwehr.manager.security;

import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
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
    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;

    @Override
    public void run(ApplicationArguments args) {
        String username = securityProperties.bootstrapAdminUsername();
        String password = securityProperties.bootstrapAdminPassword();
        String displayName = securityProperties.bootstrapAdminDisplayName();

        long activeAccounts = userRepository.countByAnonymizedAtIsNull();
        if (activeAccounts == 0) {
            userService.ensureBootstrapAdmin(username, password, displayName);
            log.warn(
                    "Erst-Administrator angelegt (Benutzername: {}). Passwort über "
                            + "FEUERWEHR_BOOTSTRAP_ADMIN_PASSWORD festlegen und in Produktion ändern.",
                    username);
            return;
        }

        Optional<User> existing = userService.findByUsername(username);
        if (existing.isEmpty() || existing.get().getAnonymizedAt() != null) {
            return;
        }

        User user = existing.get();
        if (securityProperties.bootstrapAdminResetPassword()) {
            userService.resetPassword(user.getId(), password);
            log.warn(
                    "Administrator-Passwort zurückgesetzt (Benutzer: {}). "
                            + "FEUERWEHR_BOOTSTRAP_ADMIN_RESET_PASSWORD wieder deaktivieren.",
                    username);
            return;
        }

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            userService.resetPassword(user.getId(), password);
            log.warn("Administrator ohne Passwort – Initialpasswort gesetzt (Benutzer: {}).", username);
        }
    }
}
