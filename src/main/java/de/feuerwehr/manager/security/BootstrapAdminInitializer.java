package de.feuerwehr.manager.security;

import de.feuerwehr.manager.user.UserService;
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
        if (userService.findByUsername(securityProperties.bootstrapAdminUsername()).isPresent()) {
            return;
        }
        userService.createAdminIfMissing(
                securityProperties.bootstrapAdminUsername(),
                securityProperties.bootstrapAdminPassword(),
                securityProperties.bootstrapAdminDisplayName());
        log.warn(
                "Erst-Administrator angelegt (Benutzername: {}). Passwort sofort ändern und "
                        + "FEUERWEHR_BOOTSTRAP_ADMIN_PASSWORD in Produktion setzen.",
                securityProperties.bootstrapAdminUsername());
    }
}
