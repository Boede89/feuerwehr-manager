package de.feuerwehr.manager.einsatzapp;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "feuerwehr.fcm")
public record FcmProperties(boolean enabled, String serviceAccountPath) {

    public boolean isConfigured() {
        if (!enabled || serviceAccountPath == null || serviceAccountPath.isBlank()) {
            return false;
        }
        Path path = Path.of(serviceAccountPath.trim());
        return Files.isRegularFile(path) && Files.isReadable(path);
    }
}
