package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.mail.UnitMailService;
import de.feuerwehr.manager.technik.VehicleChecklistPdfService;
import de.feuerwehr.manager.technik.VehicleChecklistService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BerichteEmailNotificationService {

    private final BerichteEmailSettingsService emailSettingsService;
    private final UnitMailService unitMailService;
    private final EinsatzberichtPdfService einsatzberichtPdfService;
    private final AnwesenheitslistePdfService anwesenheitslistePdfService;
    private final GeraetewartmitteilungPdfService geraetewartmitteilungPdfService;
    private final MaengelberichtPdfService maengelberichtPdfService;
    private final VehicleChecklistPdfService vehicleChecklistPdfService;
    private final VehicleChecklistService vehicleChecklistService;
    private final EinsatzberichtService einsatzberichtService;
    private final AnwesenheitslisteService anwesenheitslisteService;
    private final GeraetewartmitteilungService geraetewartmitteilungService;
    private final MaengelberichtService maengelberichtService;

    public void trySendOnCreate(long unitId, BerichteEmailReportType reportType, long reportId) {
        UnitBerichteEmailSettings settings = emailSettingsService.ensureSettings(unitId, reportType);
        if (!settings.isEmailEnabled()) {
            return;
        }
        if (reportType.statusTrigger()) {
            if (settings.getSendOnStatus() != IncidentReportStatus.ENTWURF) {
                return;
            }
        }
        dispatch(unitId, reportType, reportId, null, settings);
    }

    public void trySendChecklistCreated(long unitId, long vehicleId, long checklistId) {
        UnitBerichteEmailSettings settings =
                emailSettingsService.ensureSettings(unitId, BerichteEmailReportType.CHECKLISTEN);
        if (!settings.isEmailEnabled()) {
            return;
        }
        dispatch(unitId, BerichteEmailReportType.CHECKLISTEN, checklistId, vehicleId, settings);
    }

    public void trySendOnStatusChange(
            long unitId, BerichteEmailReportType reportType, long reportId, IncidentReportStatus newStatus) {
        UnitBerichteEmailSettings settings = emailSettingsService.ensureSettings(unitId, reportType);
        if (!settings.isEmailEnabled()) {
            return;
        }
        if (!reportType.statusTrigger()) {
            return;
        }
        if (settings.getSendOnStatus() != newStatus) {
            return;
        }
        dispatch(unitId, reportType, reportId, null, settings);
    }

    private void dispatch(
            long unitId,
            BerichteEmailReportType reportType,
            long reportId,
            Long vehicleId,
            UnitBerichteEmailSettings settings) {
        List<String> recipients = emailSettingsService.resolveRecipientEmails(unitId, settings);
        if (recipients.isEmpty()) {
            log.info("Berichte-E-Mail {}: keine Empfänger konfiguriert (Einheit {}).", reportType, unitId);
            return;
        }
        if (!unitMailService.canSendForUnit(unitId)) {
            log.warn("Berichte-E-Mail {}: SMTP nicht konfiguriert (Einheit {}).", reportType, unitId);
            return;
        }
        try {
            PdfPayload payload = renderPdf(unitId, reportType, reportId, vehicleId);
            String subject = "Feuerwehr-Manager: " + reportType.label();
            String body = buildBody(reportType, payload.title());
            int sent = 0;
            int failed = 0;
            for (String email : recipients) {
                var error = unitMailService.sendHtmlMail(
                        unitId, email, List.of(), subject, body, payload.filename(), payload.pdf());
                if (error.isPresent()) {
                    failed++;
                    log.warn(
                            "Berichte-E-Mail {} an {} fehlgeschlagen: {}",
                            reportType,
                            email,
                            error.get());
                } else {
                    sent++;
                }
            }
            log.info("Berichte-E-Mail {}: {} gesendet, {} fehlgeschlagen (Einheit {}).", reportType, sent, failed, unitId);
        } catch (Exception e) {
            log.warn("Berichte-E-Mail {} konnte nicht erstellt werden: {}", reportType, e.getMessage());
        }
    }

    private PdfPayload renderPdf(long unitId, BerichteEmailReportType reportType, long reportId, Long vehicleId) {
        return switch (reportType) {
            case EINSATZ -> {
                IncidentReport report = einsatzberichtService.requireReport(unitId, reportId);
                yield new PdfPayload(
                        einsatzberichtPdfService.suggestedFilename(report),
                        einsatzberichtPdfService.renderPdf(unitId, reportId),
                        reportTitle(report.getIncidentNumber(), report.getStichwort()));
            }
            case ANWESENHEIT -> {
                AttendanceReport report = anwesenheitslisteService.requireReport(unitId, reportId);
                yield new PdfPayload(
                        anwesenheitslistePdfService.suggestedFilename(report),
                        anwesenheitslistePdfService.renderPdf(unitId, reportId),
                        reportTitle(report.getReportNumber(), report.getTitle()));
            }
            case GERAETEWART -> {
                EquipmentMaintenanceReport report = geraetewartmitteilungService.requireReport(unitId, reportId);
                yield new PdfPayload(
                        geraetewartmitteilungPdfService.suggestedFilename(report),
                        geraetewartmitteilungPdfService.renderPdf(unitId, reportId),
                        reportType.label());
            }
            case MAENGEL -> {
                DefectReport report = maengelberichtService.requireReport(unitId, reportId);
                yield new PdfPayload(
                        maengelberichtPdfService.suggestedFilename(report),
                        maengelberichtPdfService.renderPdf(unitId, reportId),
                        reportType.label());
            }
            case CHECKLISTEN -> {
                if (vehicleId == null) {
                    throw new IllegalArgumentException("Fahrzeug für Checkliste fehlt.");
                }
                var detail = vehicleChecklistService
                        .getDetail(unitId, vehicleId, reportId)
                        .orElseThrow(() -> new IllegalArgumentException("Checkliste nicht gefunden."));
                yield new PdfPayload(
                        vehicleChecklistPdfService.suggestedFilename(detail),
                        vehicleChecklistPdfService.renderPdf(unitId, vehicleId, reportId),
                        detail.templateName());
            }
        };
    }

    private static String reportTitle(String number, String title) {
        if (number != null && !number.isBlank() && title != null && !title.isBlank()) {
            return number.trim() + " — " + title.trim();
        }
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (number != null && !number.isBlank()) {
            return number.trim();
        }
        return "Bericht";
    }

    private static String buildBody(BerichteEmailReportType reportType, String title) {
        String safeTitle = title != null && !title.isBlank() ? title : reportType.label();
        return """
                <!DOCTYPE html><html lang="de"><head><meta charset="UTF-8"></head>
                <body style="font-family:Arial,sans-serif;line-height:1.5;color:#333;">
                <p>Guten Tag,</p>
                <p>im Feuerwehr-Manager wurde ein neuer <strong>%s</strong> bereitgestellt:</p>
                <p><strong>%s</strong></p>
                <p>Der Bericht ist als PDF angehängt.</p>
                <p style="color:#666;font-size:12px;">Diese Nachricht wurde automatisch versendet.</p>
                </body></html>
                """
                .formatted(reportType.label(), escapeHtml(safeTitle));
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private record PdfPayload(String filename, byte[] pdf, String title) {}
}
