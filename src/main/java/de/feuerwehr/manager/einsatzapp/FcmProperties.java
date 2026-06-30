package de.feuerwehr.manager.einsatzapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "feuerwehr.fcm")
public record FcmProperties(boolean enabled, String serverKey) {

    public boolean isConfigured() {
        return enabled && serverKey != null && !serverKey.isBlank();
    }
}
