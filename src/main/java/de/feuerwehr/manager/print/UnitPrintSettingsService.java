package de.feuerwehr.manager.print;

import de.feuerwehr.manager.pdf.HtmlPdfService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitPrintSettings;
import de.feuerwehr.manager.unit.UnitPrintSettingsRepository;
import de.feuerwehr.manager.unit.UnitRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnitPrintSettingsService {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final UnitPrintSettingsRepository printSettingsRepository;
    private final UnitRepository unitRepository;
    private final CupsPrintService cupsPrintService;
    private final HtmlPdfService htmlPdfService;

    @Value("${CUPS_SERVER:}")
    private String defaultCupsServer;

    @Transactional(readOnly = true)
    public UnitPrintSettings requireSettings(long unitId) {
        return printSettingsRepository
                .findByUnitId(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Keine Druckeinstellungen für diese Einheit."));
    }

    @Transactional
    public UnitPrintSettings saveSettings(
            long unitId, PrintMode printMode, String cupsPrinterName, String cupsServer, boolean cupsUsePostscript) {
        UnitPrintSettings settings = requireSettings(unitId);
        if (printMode == null) {
            printMode = PrintMode.DIALOG;
        }
        settings.setPrintMode(printMode);
        if (printMode == PrintMode.CUPS) {
            settings.setCupsPrinterName(normalizeOptional(cupsPrinterName));
            settings.setCupsServer(normalizeOptional(cupsServer));
            settings.setCupsUsePostscript(cupsUsePostscript);
        } else {
            settings.setCupsPrinterName(null);
            settings.setCupsServer(null);
            settings.setCupsUsePostscript(false);
        }
        return printSettingsRepository.save(settings);
    }

    @Transactional(readOnly = true)
    public CupsPrintService.CupsListResult listCupsPrinters(long unitId, String cupsServerOverride) {
        UnitPrintSettings settings = requireSettings(unitId);
        String server = cupsServerOverride != null && !cupsServerOverride.isBlank()
                ? cupsServerOverride.trim()
                : resolveCupsServer(settings);
        return cupsPrintService.listPrintersDetailed(server);
    }

    @Transactional(readOnly = true)
    public List<CupsPrintService.CupsPrinterOption> listCupsPrinters(long unitId) {
        return listCupsPrinters(unitId, null).printers();
    }

    @Transactional(readOnly = true)
    public boolean isCupsClientAvailable() {
        return cupsPrintService.isCupsClientAvailable();
    }

    @Transactional
    public CupsPrintService.CupsPrintResult sendTestPrint(long unitId) {
        UnitPrintSettings settings = requireSettings(unitId);
        if (settings.getPrintMode() != PrintMode.CUPS) {
            return CupsPrintService.CupsPrintResult.failure(
                    "Testdruck nur im Modus „CUPS-Drucker“. Bitte Modus speichern und Drucker wählen.");
        }
        String printerName = settings.getCupsPrinterName();
        if (printerName == null || printerName.isBlank()) {
            return CupsPrintService.CupsPrintResult.failure("Kein CUPS-Drucker ausgewählt.");
        }
        Unit unit = unitRepository
                .findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        byte[] pdf = renderTestPdf(unit.getName());
        return cupsPrintService.printPdf(
                pdf,
                printerName,
                resolveCupsServer(settings),
                settings.isCupsUsePostscript());
    }

    public String resolveCupsServer(UnitPrintSettings settings) {
        if (settings.getCupsServer() != null && !settings.getCupsServer().isBlank()) {
            return settings.getCupsServer().trim();
        }
        if (defaultCupsServer != null && !defaultCupsServer.isBlank()) {
            return defaultCupsServer.trim();
        }
        return "";
    }

    @Transactional
    public void ensureSettings(Unit unit) {
        if (printSettingsRepository.findByUnitId(unit.getId()).isPresent()) {
            return;
        }
        UnitPrintSettings settings = new UnitPrintSettings();
        settings.setUnit(unit);
        settings.setPrintMode(PrintMode.DIALOG);
        printSettingsRepository.save(settings);
    }

    private byte[] renderTestPdf(String unitName) {
        Map<String, Object> model = Map.of(
                "unitName", unitName != null ? unitName : "Einheit",
                "timestamp", TIMESTAMP.format(LocalDateTime.now()) + " Uhr");
        return htmlPdfService.renderPdf("print/test-page", model);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
