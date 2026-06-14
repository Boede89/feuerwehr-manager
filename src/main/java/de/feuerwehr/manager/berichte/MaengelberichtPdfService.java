package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.pdf.HtmlPdfService;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaengelberichtPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final MaengelberichtService maengelberichtService;
    private final HtmlPdfService htmlPdfService;

    @Transactional(readOnly = true)
    public byte[] renderPdf(long unitId, long reportId) {
        DefectReport report = maengelberichtService.requireReport(unitId, reportId);
        Map<String, Object> model = buildModel(report);
        return htmlPdfService.renderPdf("berichte/maengel-druck", model);
    }

    public String suggestedFilename(DefectReport report) {
        String date = report.getAufgenommenAm() != null
                ? report.getAufgenommenAm().format(DateTimeFormatter.ISO_DATE)
                : "ohne-datum";
        MaengelberichtStandort standort =
                report.getStandort() != null ? report.getStandort() : MaengelberichtStandort.GH_AMERN;
        String standortSlug = standort.label().replaceAll("[^a-zA-Z0-9_-]", "_");
        return "Maengelbericht_" + date + "_" + standortSlug + ".pdf";
    }

    private Map<String, Object> buildModel(DefectReport report) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("unitLogoBase64", report.getUnit().getLogoBase64());
        model.put(
                "standort",
                report.getStandort() != null ? report.getStandort().label() : "—");
        model.put(
                "mangelAn",
                report.getMangelAn() != null ? report.getMangelAn().label() : "—");
        model.put("bezeichnung", displayOrDash(report.getBezeichnung()));
        model.put("vehicleDisplay", maengelberichtService.resolveVehicleDisplay(report));
        model.put("mangelBeschreibung", displayOrDash(report.getMangelBeschreibung()));
        model.put("mangelBeschreibungHtml", toHtmlMultiline(report.getMangelBeschreibung()));
        model.put("ursache", displayOrDash(report.getUrsache()));
        model.put("verbleib", displayOrDash(report.getVerbleib()));
        model.put("recordedByDisplay", maengelberichtService.resolveRecordedByDisplay(report));
        model.put(
                "aufgenommenAm",
                report.getAufgenommenAm() != null ? report.getAufgenommenAm().format(DATE_FMT) : "—");
        return model;
    }

    private static String toHtmlMultiline(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        return escHtml(value.trim()).replace("\r\n", "<br/>").replace("\n", "<br/>").replace("\r", "<br/>");
    }

    private static String escHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String displayOrDash(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        return value.trim();
    }
}
