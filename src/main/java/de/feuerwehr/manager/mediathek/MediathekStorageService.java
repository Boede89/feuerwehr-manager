package de.feuerwehr.manager.mediathek;

import de.feuerwehr.manager.config.StorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MediathekStorageService {

    public static final long MAX_FILE_SIZE = 40L * 1024L * 1024L;

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_GIF_VALUE,
            "image/webp",
            MediaType.APPLICATION_PDF_VALUE,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text");

    private final StorageProperties storageProperties;

    public StoredUpload store(long folderId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Bitte eine Datei auswählen.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Datei zu groß (max. 40 MB).");
        }
        String originalName = sanitizeFilename(file.getOriginalFilename());
        String mimeType = resolveMimeType(file, originalName);
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException(
                    "Dateityp nicht erlaubt. Erlaubt: Bilder, PDF, Word (docx), PowerPoint (pptx), ODT.");
        }
        try {
            Path dir = folderDir(folderId);
            Files.createDirectories(dir);
            String storedName = "file-" + UUID.randomUUID().toString().replace("-", "") + extensionOf(originalName);
            Path target = dir.resolve(storedName);
            Files.write(target, file.getBytes());
            return new StoredUpload(originalName, storedName, mimeType, file.getSize());
        } catch (IOException e) {
            throw new IllegalArgumentException("Datei konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public Resource load(long folderId, String storedName) {
        Path path = folderDir(folderId).resolve(storedName);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Datei nicht gefunden.");
        }
        return new FileSystemResource(path);
    }

    public void deleteFile(long folderId, String storedName) {
        try {
            Files.deleteIfExists(folderDir(folderId).resolve(storedName));
        } catch (IOException ignored) {
            // bleibt ggf. liegen
        }
    }

    public void deleteFolderDirectory(long folderId) {
        Path dir = folderDir(folderId);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // nächster Versuch
                }
            });
        } catch (IOException ignored) {
            // Verzeichnis bleibt ggf. bestehen
        }
    }

    private Path folderDir(long folderId) {
        return Path.of(storageProperties.getDataDir(), "mediathek", String.valueOf(folderId));
    }

    private static String sanitizeFilename(String original) {
        String name = original == null || original.isBlank() ? "datei" : original.trim();
        name = name.replace('\\', '_').replace('/', '_').replace('"', '\'');
        return name.isBlank() ? "datei" : name;
    }

    private static String resolveMimeType(MultipartFile file, String filename) {
        String mimeType = file.getContentType();
        if (mimeType != null && !mimeType.isBlank() && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(mimeType)) {
            return mimeType;
        }
        return switch (extensionOf(filename).replace(".", "").toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG_VALUE;
            case "png" -> MediaType.IMAGE_PNG_VALUE;
            case "gif" -> MediaType.IMAGE_GIF_VALUE;
            case "webp" -> "image/webp";
            case "pdf" -> MediaType.APPLICATION_PDF_VALUE;
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "odt" -> "application/vnd.oasis.opendocument.text";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    public record StoredUpload(String originalName, String storedName, String mimeType, long fileSize) {}
}
