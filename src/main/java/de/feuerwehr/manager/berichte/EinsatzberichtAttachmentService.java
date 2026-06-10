package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.config.StorageProperties;
import de.feuerwehr.manager.user.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class EinsatzberichtAttachmentService {

    private static final long MAX_ATTACHMENT_SIZE = 20L * 1024L * 1024L;

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_GIF_VALUE,
            "image/webp",
            MediaType.APPLICATION_PDF_VALUE,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.oasis.opendocument.text",
            MediaType.TEXT_PLAIN_VALUE);

    private final IncidentReportAttachmentRepository attachmentRepository;
    private final IncidentReportRepository incidentReportRepository;
    private final UserRepository userRepository;
    private final StorageProperties storageProperties;

    @Transactional(readOnly = true)
    public List<IncidentAttachmentDto> list(long unitId, long reportId) {
        requireReport(unitId, reportId);
        return attachmentRepository.findByIncidentReportIdOrderByCreatedAtAsc(reportId).stream()
                .map(IncidentAttachmentDto::from)
                .toList();
    }

    @Transactional
    public IncidentAttachmentDto upload(long unitId, long reportId, MultipartFile file, Long userId) {
        IncidentReport report = requireEditableReport(unitId, reportId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Keine Datei im Request gefunden.");
        }
        if (file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new IllegalArgumentException("Datei zu groß (max. 20 MB).");
        }

        String filename = sanitizeFilename(file.getOriginalFilename());
        String mimeType = resolveMimeType(file, filename);
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException(
                    "Dateityp '" + mimeType + "' ist nicht erlaubt. Erlaubt: Bilder (JPEG/PNG/GIF/WebP), PDF, Word (docx), ODT, Text.");
        }

        String extension = extensionOf(filename);
        String storedName = UUID.randomUUID() + "." + extension;
        Path target = attachmentPath(reportId).resolve(storedName);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new IllegalArgumentException("Datei konnte nicht gespeichert werden: " + e.getMessage());
        }

        IncidentReportAttachment attachment = new IncidentReportAttachment();
        attachment.setIncidentReport(report);
        attachment.setFilename(filename);
        attachment.setStoredName(storedName);
        attachment.setMimeType(mimeType);
        attachment.setFileSize(file.getSize());
        if (userId != null) {
            userRepository.findById(userId).ifPresent(attachment::setUploadedByUser);
        }
        return IncidentAttachmentDto.from(attachmentRepository.save(attachment));
    }

    @Transactional(readOnly = true)
    public DownloadFile download(long unitId, long reportId, long attachmentId) {
        requireReport(unitId, reportId);
        IncidentReportAttachment attachment = attachmentRepository
                .findByIdAndIncidentReportId(attachmentId, reportId)
                .orElseThrow(() -> new IllegalArgumentException("Anhang nicht gefunden."));
        Path filePath = attachmentPath(reportId).resolve(attachment.getStoredName());
        if (!Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException("Anhang-Datei nicht gefunden.");
        }
        Resource resource = new FileSystemResource(filePath);
        return new DownloadFile(resource, attachment.getFilename(), attachment.getMimeType(), attachment.getFileSize());
    }

    @Transactional
    public void delete(long unitId, long reportId, long attachmentId) {
        requireEditableReport(unitId, reportId);
        IncidentReportAttachment attachment = attachmentRepository
                .findByIdAndIncidentReportId(attachmentId, reportId)
                .orElseThrow(() -> new IllegalArgumentException("Anhang nicht gefunden."));
        deleteStoredFile(reportId, attachment.getStoredName());
        attachmentRepository.delete(attachment);
    }

    @Transactional
    public void deleteAllForReport(long reportId) {
        attachmentRepository.deleteByIncidentReportId(reportId);
        deleteReportDirectory(reportId);
    }

    private IncidentReport requireReport(long unitId, long reportId) {
        return incidentReportRepository
                .findByIdAndUnitId(reportId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einsatzbericht nicht gefunden."));
    }

    private IncidentReport requireEditableReport(long unitId, long reportId) {
        IncidentReport report = requireReport(unitId, reportId);
        if (report.getStatus() != IncidentReportStatus.ENTWURF) {
            throw new IllegalArgumentException("Anhänge können nur bei Entwürfen bearbeitet werden.");
        }
        return report;
    }

    private Path attachmentPath(long reportId) {
        return Path.of(storageProperties.getDataDir(), "incidents", String.valueOf(reportId));
    }

    private void deleteStoredFile(long reportId, String storedName) {
        try {
            Files.deleteIfExists(attachmentPath(reportId).resolve(storedName));
        } catch (IOException ignored) {
            // Datei war ggf. schon entfernt
        }
    }

    private void deleteReportDirectory(long reportId) {
        Path dir = attachmentPath(reportId);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Verzeichnis wird beim nächsten Löschversuch erneut versucht
                }
            });
        } catch (IOException ignored) {
            // Verzeichnis bleibt ggf. mit Restdateien bestehen
        }
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
        return switch (extensionOf(filename)) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG_VALUE;
            case "png" -> MediaType.IMAGE_PNG_VALUE;
            case "gif" -> MediaType.IMAGE_GIF_VALUE;
            case "webp" -> "image/webp";
            case "pdf" -> MediaType.APPLICATION_PDF_VALUE;
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "odt" -> "application/vnd.oasis.opendocument.text";
            case "txt" -> MediaType.TEXT_PLAIN_VALUE;
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "bin";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record DownloadFile(Resource resource, String filename, String mimeType, long fileSize) {}
}
