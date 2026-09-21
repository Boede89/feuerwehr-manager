package de.feuerwehr.manager.leitstellen;

import static org.assertj.core.api.Assertions.assertThat;

import de.feuerwehr.manager.berichte.IncidentReport;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class LeitstellenPdfReporterSupportTest {

    private final LeitstellenPdfReporterExtractor extractor = new LeitstellenPdfReporterExtractor();

    @Test
    void parseSameLineWithSlash() {
        var contact = LeitstellenPdfReporterSupport.parse("Meldender Järchel/004915221009673").orElseThrow();
        assertThat(contact.name()).isEqualTo("Järchel");
        assertThat(contact.phone()).isEqualTo("004915221009673");
    }

    @Test
    void parseColonAndSpacesAroundSlash() {
        var contact = LeitstellenPdfReporterSupport.parse(
                        "Stichwort: H1\nMeldender: Järchel / 004915221009673\nMeldeweg: Telefon")
                .orElseThrow();
        assertThat(contact.name()).isEqualTo("Järchel");
        assertThat(contact.phone()).isEqualTo("004915221009673");
    }

    @Test
    void parseValueOnNextLine() {
        var contact = LeitstellenPdfReporterSupport.parse("Meldender:\nJärchel/004915221009673\nMeldeweg: Notruf")
                .orElseThrow();
        assertThat(contact.name()).isEqualTo("Järchel");
        assertThat(contact.phone()).isEqualTo("004915221009673");
    }

    @Test
    void parseGermanMobileWithoutSlash() {
        var contact = LeitstellenPdfReporterSupport.parse("Meldender Max Mustermann 015221009673").orElseThrow();
        assertThat(contact.name()).isEqualTo("Max Mustermann");
        assertThat(contact.phone()).isEqualTo("015221009673");
    }

    @Test
    void parseTableLayoutWithHeaderRow() {
        var contact = LeitstellenPdfReporterSupport.parse(
                        "Meldender Meldeweg\nJärchel/004915221009673 Telefon")
                .orElseThrow();
        assertThat(contact.name()).isEqualTo("Järchel");
        assertThat(contact.phone()).isEqualTo("004915221009673");
    }

    @Test
    void applyIfMissingFillsBlankFieldsOnly() {
        IncidentReport report = new IncidentReport();
        report.setReporterName("Bereits gesetzt");
        var contact = new LeitstellenPdfReporterSupport.ReporterContact("Järchel", "004915221009673");

        assertThat(LeitstellenPdfReporterSupport.applyIfMissing(report, contact)).isTrue();
        assertThat(report.getReporterName()).isEqualTo("Bereits gesetzt");
        assertThat(report.getReporterPhone()).isEqualTo("004915221009673");
    }

    @Test
    void splitCombinedReporterName() {
        IncidentReport report = new IncidentReport();
        report.setReporterName("Järchel/004915221009673");

        assertThat(LeitstellenPdfReporterSupport.splitCombinedReporterNameIfNeeded(report)).isTrue();
        assertThat(report.getReporterName()).isEqualTo("Järchel");
        assertThat(report.getReporterPhone()).isEqualTo("004915221009673");
    }

    @Test
    void ignoresMissingLabel() {
        assertThat(LeitstellenPdfReporterSupport.parse("Stichwort H1 Järchel/004915221009673")).isEmpty();
    }

    @Test
    void extractFromSearchablePdf() throws Exception {
        byte[] pdf = searchablePdf("Meldender Järchel/004915221009673");
        var contact = extractor.extract(pdf).orElseThrow();
        assertThat(contact.name()).isEqualTo("Järchel");
        assertThat(contact.phone()).isEqualTo("004915221009673");
    }

    private static byte[] searchablePdf(String line) throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(line);
                stream.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
