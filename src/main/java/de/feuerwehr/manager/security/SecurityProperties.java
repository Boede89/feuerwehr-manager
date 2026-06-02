package de.feuerwehr.manager.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "feuerwehr.security")
public record SecurityProperties(
        String bootstrapAdminUsername,
        String bootstrapAdminPassword,
        String bootstrapAdminDisplayName,
        int sessionTimeoutMinutes,
        boolean rfidApiEnabled,
        boolean bootstrapAdminResetPassword,
        int minPasswordLength
) {
    public SecurityProperties {
        if (minPasswordLength < 4) {
            minPasswordLength = 4;
        }
        if (sessionTimeoutMinutes < 5) {
            sessionTimeoutMinutes = 480;
        }
        if (bootstrapAdminUsername == null || bootstrapAdminUsername.isBlank()) {
            bootstrapAdminUsername = "admin";
        }
        if (bootstrapAdminDisplayName == null || bootstrapAdminDisplayName.isBlank()) {
            bootstrapAdminDisplayName = "Administrator";
        }
    }
}
