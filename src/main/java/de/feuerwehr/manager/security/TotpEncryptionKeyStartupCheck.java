package de.feuerwehr.manager.security;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class TotpEncryptionKeyStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TotpEncryptionKeyStartupCheck.class);

    private final SecurityProperties securityProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (StringUtils.hasText(securityProperties.totpEncryptionKey())) {
            log.info("2FA-TOTP-Secrets werden verschlüsselt in der Datenbank gespeichert (AES-256-GCM).");
            return;
        }
        log.warn(
                "DSGVO/2FA: FEUERWEHR_TOTP_ENCRYPTION_KEY ist nicht gesetzt — TOTP-Geheimnisse werden "
                        + "unverschlüsselt (plain:) in der Datenbank abgelegt. In Produktion zwingend setzen "
                        + "(siehe .env.example und docs/DSGVO.md). Key nach dem ersten 2FA-Setup nicht mehr ändern.");
    }
}
