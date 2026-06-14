package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.pdf.HtmlPdfService;
import de.feuerwehr.manager.unit.Unit;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GeraetewartmitteilungPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final GeraetewartmitteilungService geraetewartmitteilungService;
    private final HtmlPdfService htmlPdfService;

    @Transactional(readOnly = true)
    public byte[] renderPdf(long unitId, long reportId) {
        EquipmentMaintenanceReport report = geraetewartmitteilungService.requireReport(unitId, reportId);
        Map<String, Object> model = buildModel(unitId, report);
        return htmlPdfService.renderPdf("berichte/geraetewart-druck", model);
    }

    public String suggestedFilename(EquipmentMaintenanceReport report) {
        String date = report.getEventDate() != null
                ? report.getEventDate().format(DateTimeFormatter.ISO_DATE)
                : "ohne-datum";
        GeraetewartEventArt art =
                report.getEventArt() != null ? report.getEventArt() : GeraetewartEventArt.BRANDEINSATZ;
        String artSlug = art.label().replaceAll("[^a-zA-Z0-9_-]", "_");
        return "Geraetewartmitteilung_" + date + "_" + artSlug + ".pdf";
    }

    private Map<String, Object> buildModel(long unitId, EquipmentMaintenanceReport report) {
        Unit unit = report.getUnit();
        GeraetewartTyp typ = report.getTyp() != null ? report.getTyp() : GeraetewartTyp.UEBUNG;
        GeraetewartEventArt eventArt =
                report.getEventArt() != null ? report.getEventArt() : GeraetewartEventArt.BRANDEINSATZ;
        GeraetewartReadiness readiness =
                report.getReadiness() != null ? report.getReadiness() : GeraetewartReadiness.HERGESTELLT;
        List<GeraetewartmitteilungService.GeraetewartPdfVehicleRow> vehicles =
                geraetewartmitteilungService.buildVehicleRows(unitId, report);

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("unitLogoBase64", unit.getLogoBase64());
        model.put("typLabel", typ.label());
        model.put("eventArtLabel", eventArt.label());
        model.put(
                "eventDate",
                report.getEventDate() != null ? report.getEventDate().format(DATE_FMT) : "—");
        model.put("readinessLabel", readiness.label());
        model.put("leaderLabel", geraetewartmitteilungService.leaderFieldLabel(typ));
        model.put("leaderDisplay", geraetewartmitteilungService.resolveLeaderDisplay(report));
        model.put("vehicles", vehicles);
        return model;
    }
}
