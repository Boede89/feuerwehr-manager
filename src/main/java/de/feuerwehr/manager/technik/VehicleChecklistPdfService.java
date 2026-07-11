package de.feuerwehr.manager.technik;

import de.feuerwehr.manager.pdf.HtmlPdfService;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VehicleChecklistPdfService {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY);

    private final VehicleChecklistService checklistService;
    private final HtmlPdfService htmlPdfService;

    @Transactional(readOnly = true)
    public byte[] renderPdf(long unitId, long vehicleId, long checklistId) {
        ChecklistDetailRow detail = checklistService
                .getDetail(unitId, vehicleId, checklistId)
                .orElseThrow(() -> new IllegalArgumentException("Checkliste nicht gefunden."));
        Map<String, Object> model = new HashMap<>();
        model.put("templateName", detail.templateName());
        model.put(
                "filledAt",
                detail.filledAt() != null ? TS_FMT.format(detail.filledAt().atZone(BERLIN)) : "—");
        model.put("filledName", detail.filledName());
        model.put("notes", detail.notes());
        List<Map<String, String>> rows = detail.entries().stream()
                .map(entry -> {
                    Map<String, String> row = new HashMap<>();
                    row.put("itemLabel", entry.itemLabel());
                    row.put("resultLabel", resultLabel(entry.result()));
                    row.put("note", entry.note());
                    return row;
                })
                .toList();
        model.put("entries", rows);
        return htmlPdfService.renderPdf("technik/checklist-druck", model);
    }

    public String suggestedFilename(ChecklistDetailRow detail) {
        String name = detail.templateName() != null
                ? detail.templateName().replaceAll("[^a-zA-Z0-9._-]", "_")
                : "Checkliste";
        return "Checkliste-" + name + ".pdf";
    }

    private static String resultLabel(String resultKey) {
        return switch (ChecklistResult.fromKey(resultKey)) {
            case OK -> "OK";
            case MANGEL -> "Mangel";
            case NICHT_GEPRUEFT -> "Nicht geprüft";
        };
    }
}
