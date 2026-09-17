package de.feuerwehr.manager.uvv;

import de.feuerwehr.manager.config.StorageProperties;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UvvPresentationService {

    public static final long MAX_PRESENTATION_SIZE = 40L * 1024L * 1024L;
    public static final int MAX_PAGES = 120;
    private static final float RENDER_DPI = 144f;

    private final UvvCampaignRepository campaignRepository;
    private final StorageProperties storageProperties;

    @Transactional
    public UvvCampaign storePresentation(long unitId, long campaignId, MultipartFile file) {
        UvvCampaign campaign = requireCampaign(unitId, campaignId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Bitte eine PDF-Datei auswählen.");
        }
        if (file.getSize() > MAX_PRESENTATION_SIZE) {
            throw new IllegalArgumentException("Datei zu groß (max. 40 MB).");
        }
        String originalName = sanitizeFilename(file.getOriginalFilename());
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")
                && !MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(safeContentType(file))) {
            throw new IllegalArgumentException("Nur PDF-Präsentationen sind erlaubt (z. B. aus PowerPoint als PDF exportieren).");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Datei konnte nicht gelesen werden.");
        }
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F') {
            throw new IllegalArgumentException("Die Datei ist kein gültiges PDF.");
        }

        clearPresentationFiles(campaign.getId());
        Path campaignDir = campaignDir(campaign.getId());
        Path slidesDir = slidesDir(campaign.getId());
        try {
            Files.createDirectories(slidesDir);
            String storedName = "presentation-" + UUID.randomUUID().toString().replace("-", "") + ".pdf";
            Path pdfPath = campaignDir.resolve(storedName);
            Files.write(pdfPath, bytes);

            int pages;
            try (PDDocument document = Loader.loadPDF(bytes)) {
                pages = document.getNumberOfPages();
                if (pages <= 0) {
                    throw new IllegalArgumentException("Das PDF enthält keine Seiten.");
                }
                if (pages > MAX_PAGES) {
                    throw new IllegalArgumentException("Zu viele Folien (max. " + MAX_PAGES + ").");
                }
                PDFRenderer renderer = new PDFRenderer(document);
                for (int i = 0; i < pages; i++) {
                    BufferedImage image = renderer.renderImageWithDPI(i, RENDER_DPI, ImageType.RGB);
                    Path slidePath = slidesDir.resolve(slideFileName(i + 1));
                    ImageIO.write(image, "png", slidePath.toFile());
                }
            }

            campaign.setPresentationOriginalName(originalName.endsWith(".pdf") ? originalName : originalName + ".pdf");
            campaign.setPresentationStoredName(storedName);
            campaign.setPresentationMimeType(MediaType.APPLICATION_PDF_VALUE);
            campaign.setPresentationPageCount(pages);
            return campaignRepository.save(campaign);
        } catch (IllegalArgumentException e) {
            clearPresentationFiles(campaign.getId());
            clearPresentationMetadata(campaign);
            campaignRepository.save(campaign);
            throw e;
        } catch (IOException e) {
            clearPresentationFiles(campaign.getId());
            clearPresentationMetadata(campaign);
            campaignRepository.save(campaign);
            throw new IllegalArgumentException("Präsentation konnte nicht verarbeitet werden: " + e.getMessage());
        }
    }

    @Transactional
    public void deletePresentation(long unitId, long campaignId) {
        UvvCampaign campaign = requireCampaign(unitId, campaignId);
        clearPresentationFiles(campaign.getId());
        clearPresentationMetadata(campaign);
        campaignRepository.save(campaign);
    }

    @Transactional
    public void deleteAllFilesForCampaign(long campaignId) {
        clearPresentationFiles(campaignId);
        deleteDirectoryQuietly(campaignDir(campaignId));
    }

    @Transactional(readOnly = true)
    public SlideFile loadSlide(long unitId, long campaignId, int page) {
        UvvCampaign campaign = requireCampaign(unitId, campaignId);
        if (!campaign.hasPresentation()) {
            throw new IllegalArgumentException("Für diese Kampagne ist keine Präsentation hinterlegt.");
        }
        if (page < 1 || page > campaign.getPresentationPageCount()) {
            throw new IllegalArgumentException("Folie nicht gefunden.");
        }
        Path slidePath = slidesDir(campaignId).resolve(slideFileName(page));
        if (!Files.isRegularFile(slidePath)) {
            throw new IllegalArgumentException("Folie nicht gefunden.");
        }
        return new SlideFile(
                new FileSystemResource(slidePath),
                "folie-" + page + ".png",
                MediaType.IMAGE_PNG_VALUE,
                slidePath.toFile().length());
    }

    private UvvCampaign requireCampaign(long unitId, long campaignId) {
        return campaignRepository
                .findByIdAndUnitId(campaignId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Kampagne nicht gefunden."));
    }

    private void clearPresentationMetadata(UvvCampaign campaign) {
        campaign.setPresentationOriginalName(null);
        campaign.setPresentationStoredName(null);
        campaign.setPresentationMimeType(null);
        campaign.setPresentationPageCount(null);
    }

    private void clearPresentationFiles(long campaignId) {
        deleteDirectoryQuietly(slidesDir(campaignId));
        Path dir = campaignDir(campaignId);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // nächster Versuch beim Überschreiben
                }
            });
        } catch (IOException ignored) {
            // Verzeichnis bleibt ggf. mit Restbestanden
        }
    }

    private Path campaignDir(long campaignId) {
        return Path.of(storageProperties.getDataDir(), "uvv", String.valueOf(campaignId));
    }

    private Path slidesDir(long campaignId) {
        return campaignDir(campaignId).resolve("slides");
    }

    private static String slideFileName(int page) {
        return String.format(Locale.ROOT, "slide-%03d.png", page);
    }

    private static void deleteDirectoryQuietly(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // bleibt ggf. liegen
                }
            });
        } catch (IOException ignored) {
            // Verzeichnis bleibt ggf. bestehen
        }
    }

    private static String sanitizeFilename(String original) {
        String name = original == null || original.isBlank() ? "praesentation.pdf" : original.trim();
        name = name.replace('\\', '_').replace('/', '_').replace('"', '\'');
        return name.isBlank() ? "praesentation.pdf" : name;
    }

    private static String safeContentType(MultipartFile file) {
        String type = file.getContentType();
        return type == null ? "" : type.trim();
    }

    public record SlideFile(Resource resource, String filename, String mimeType, long fileSize) {}
}
