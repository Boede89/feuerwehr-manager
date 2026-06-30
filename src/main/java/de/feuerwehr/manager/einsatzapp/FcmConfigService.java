package de.feuerwehr.manager.einsatzapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.config.StorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmConfigService {

    private static final String RELATIVE_PATH = "einsatzapp/fcm-service-account.json";
    private static final long MAX_BYTES = 256 * 1024;

    private final StorageProperties storageProperties;
    private final FcmProperties fcmProperties;
    private final ObjectMapper objectMapper;

    public boolean isConfigured() {
        return resolveEffectivePath().filter(this::isValidServiceAccountFile).isPresent();
    }

    public boolean hasUploadedServiceAccount() {
        Path uploaded = uploadedPath();
        return Files.isRegularFile(uploaded) && Files.isReadable(uploaded);
    }

    public Optional<String> configuredProjectId() {
        return resolveEffectivePath().flatMap(this::readProjectId);
    }

    public Optional<String> configuredClientEmail() {
        return resolveEffectivePath().flatMap(this::readClientEmail);
    }

    public Optional<Path> resolveEffectivePath() {
        Path uploaded = uploadedPath();
        if (Files.isRegularFile(uploaded) && Files.isReadable(uploaded)) {
            return Optional.of(uploaded);
        }
        if (!fcmProperties.enabled()) {
            return Optional.empty();
        }
        String envPath = fcmProperties.serviceAccountPath();
        if (envPath == null || envPath.isBlank()) {
            return Optional.empty();
        }
        Path path = Path.of(envPath.trim());
        if (Files.isRegularFile(path) && Files.isReadable(path)) {
            return Optional.of(path);
        }
        return Optional.empty();
    }

    public void saveUploadedServiceAccount(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Bitte eine JSON-Datei auswählen.");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (!name.endsWith(".json")) {
            throw new IllegalArgumentException("Nur JSON-Dateien (.json) sind erlaubt.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Die Datei ist zu groß (max. 256 KB).");
        }
        byte[] bytes = file.getBytes();
        validateServiceAccountBytes(bytes);

        Path target = uploadedPath();
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temp, bytes);
        validateServiceAccountFile(temp);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        restrictPermissions(target);
        log.info("[FCM] Dienstkonto per Web-UI gespeichert ({})", target);
    }

    public void deleteUploadedServiceAccount() throws IOException {
        Path target = uploadedPath();
        if (!Files.exists(target)) {
            return;
        }
        Files.delete(target);
        log.info("[FCM] Hochgeladenes Dienstkonto entfernt");
    }

    private Path uploadedPath() {
        return Path.of(storageProperties.getDataDir(), RELATIVE_PATH).toAbsolutePath().normalize();
    }

    private void validateServiceAccountBytes(byte[] bytes) throws IOException {
        JsonNode root = objectMapper.readTree(bytes);
        validateServiceAccountJson(root);
    }

    private boolean isValidServiceAccountFile(Path path) {
        try {
            validateServiceAccountFile(path);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void validateServiceAccountFile(Path path) throws IOException {
        JsonNode root = objectMapper.readTree(Files.readString(path));
        validateServiceAccountJson(root);
    }

    private static void validateServiceAccountJson(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Ungültige JSON-Datei.");
        }
        String type = root.path("type").asText("");
        if (!"service_account".equals(type)) {
            throw new IllegalArgumentException("Keine Firebase-Dienstkonto-Datei (type muss service_account sein).");
        }
        if (root.path("project_id").asText("").isBlank()) {
            throw new IllegalArgumentException("project_id fehlt in der JSON-Datei.");
        }
        if (root.path("private_key").asText("").isBlank()) {
            throw new IllegalArgumentException("private_key fehlt in der JSON-Datei.");
        }
        if (root.path("client_email").asText("").isBlank()) {
            throw new IllegalArgumentException("client_email fehlt in der JSON-Datei.");
        }
    }

    private Optional<String> readProjectId(Path path) {
        try {
            JsonNode root = objectMapper.readTree(Files.readString(path));
            String id = root.path("project_id").asText("");
            return id.isBlank() ? Optional.empty() : Optional.of(id);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<String> readClientEmail(Path path) {
        try {
            JsonNode root = objectMapper.readTree(Files.readString(path));
            String email = root.path("client_email").asText("");
            return email.isBlank() ? Optional.empty() : Optional.of(email);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static void restrictPermissions(Path path) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, perms);
        } catch (Exception ignored) {
            // Windows o. ä. — keine POSIX-Rechte
        }
    }
}
