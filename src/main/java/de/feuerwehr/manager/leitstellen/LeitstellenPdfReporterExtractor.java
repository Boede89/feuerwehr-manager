package de.feuerwehr.manager.leitstellen;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LeitstellenPdfReporterExtractor {

    public Optional<LeitstellenPdfReporterSupport.ReporterContact> extract(byte[] pdfBytes) {
        String text = extractText(pdfBytes);
        return LeitstellenPdfReporterSupport.parse(text);
    }

    public String extractText(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length < 5) {
            return "";
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            return text == null ? "" : text;
        } catch (Exception e) {
            log.debug("Leitstellen-PDF-Text konnte nicht gelesen werden: {}", e.getMessage());
            return "";
        }
    }
}
