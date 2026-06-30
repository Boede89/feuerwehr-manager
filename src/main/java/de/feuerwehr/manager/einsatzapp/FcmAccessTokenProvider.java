package de.feuerwehr.manager.einsatzapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** OAuth2-Zugriffstoken aus Firebase-Dienstkonto-JSON (FCM HTTP v1). */
@Service
@RequiredArgsConstructor
@Slf4j
public class FcmAccessTokenProvider {

    private static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final FcmProperties fcmProperties;
    private final ObjectMapper objectMapper;

    private volatile GoogleCredentials credentials;
    private volatile String projectId;

    public Optional<String> getProjectId() {
        if (!fcmProperties.isConfigured()) {
            return Optional.empty();
        }
        ensureLoaded();
        return projectId != null && !projectId.isBlank() ? Optional.of(projectId) : Optional.empty();
    }

    public Optional<String> getAccessToken() {
        if (!fcmProperties.isConfigured()) {
            return Optional.empty();
        }
        try {
            ensureLoaded();
            if (credentials == null) {
                return Optional.empty();
            }
            credentials.refreshIfExpired();
            if (credentials.getAccessToken() == null) {
                return Optional.empty();
            }
            return Optional.of(credentials.getAccessToken().getTokenValue());
        } catch (IOException e) {
            log.warn("[FCM] Zugriffstoken nicht abrufbar: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void ensureLoaded() {
        if (credentials != null && projectId != null) {
            return;
        }
        synchronized (this) {
            if (credentials != null && projectId != null) {
                return;
            }
            loadCredentials();
        }
    }

    private void loadCredentials() {
        Path path = Path.of(fcmProperties.serviceAccountPath().trim());
        try (InputStream in = Files.newInputStream(path)) {
            JsonNode root = objectMapper.readTree(in);
            projectId = root.path("project_id").asText(null);
            if (projectId == null || projectId.isBlank()) {
                log.warn("[FCM] project_id fehlt in {}", path);
                credentials = null;
                return;
            }
            try (InputStream credIn = Files.newInputStream(path)) {
                credentials = GoogleCredentials.fromStream(credIn).createScoped(Collections.singleton(FCM_SCOPE));
            }
            log.info("[FCM] Dienstkonto geladen (Projekt {})", projectId);
        } catch (IOException e) {
            log.warn("[FCM] Dienstkonto nicht lesbar ({}): {}", path, e.getMessage());
            credentials = null;
            projectId = null;
        }
    }
}
