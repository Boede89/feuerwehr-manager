package de.feuerwehr.manager.leitstellen;

import de.feuerwehr.manager.berichte.EinsatzberichtAttachmentService;
import de.feuerwehr.manager.berichte.IncidentReport;
import de.feuerwehr.manager.berichte.IncidentReportAttachmentRepository;
import de.feuerwehr.manager.berichte.IncidentReportRepository;
import de.feuerwehr.manager.berichte.IncidentReportStatus;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeitstellenMailImportService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final UnitLeitstellenMailSettingsRepository settingsRepository;
    private final LeitstellenMailImportRepository importRepository;
    private final IncidentReportRepository incidentReportRepository;
    private final IncidentReportAttachmentRepository attachmentRepository;
    private final EinsatzberichtAttachmentService attachmentService;
    private final LeitstellenImapClient imapClient;
    private final LeitstellenMailMatcher matcher;
    private final TestModeService testModeService;

    public record PollResult(
            int fetchedMails,
            int pdfAttachmentsFound,
            int importedAttachments,
            int skipped,
            int unmatched,
            String message) {}

    @Transactional
    public PollResult pollUnit(long unitId) {
        UnitLeitstellenMailSettings settings = requireEnabledSettings(unitId, false);
        if (settings == null) {
            return disabledOrMissing(unitId);
        }
        List<IncidentReport> candidates = loadCandidates(unitId, settings);
        candidates = candidates.stream().filter(r -> !alreadyComplete(r.getId())).toList();
        if (candidates.isEmpty()) {
            return finish(
                    settings,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "Kein Abruf nötig: für alle Berichte im Lookback liegen Depeche und Abschlussbericht bereits vor "
                            + "(oder es gibt keine passenden Berichte).");
        }
        return pollInternal(settings, candidates, null);
    }

    /**
     * Einmaliger Abruf nur für einen Einsatzbericht (auch freigegeben), sofern Dateien fehlen.
     */
    @Transactional
    public PollResult pollForReport(long unitId, long reportId) {
        UnitLeitstellenMailSettings settings = requireEnabledSettings(unitId, true);
        if (settings == null) {
            UnitLeitstellenMailSettings raw = settingsRepository.findByUnitId(unitId).orElse(null);
            if (raw == null || raw.getImapHost() == null || raw.getImapHost().isBlank()) {
                return new PollResult(0, 0, 0, 0, 0, "Leitstellen-Mail ist nicht konfiguriert.");
            }
            if (!raw.isEnabled()) {
                return new PollResult(0, 0, 0, 0, 0, "Leitstellen-Mail-Abruf ist deaktiviert.");
            }
            return new PollResult(0, 0, 0, 0, 0, "IMAP-Host fehlt.");
        }
        IncidentReport report = incidentReportRepository
                .findById(reportId)
                .filter(r -> r.getUnit() != null && r.getUnit().getId() == unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einsatzbericht nicht gefunden."));
        if (report.getStatus() == IncidentReportStatus.ARCHIVIERT) {
            return finish(settings, 0, 0, 0, 0, 0, "Archivierte Berichte werden nicht ergänzt.");
        }
        if (alreadyComplete(reportId)) {
            return finish(
                    settings,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "Depeche und Abschlussbericht sind bereits hinterlegt — kein Abruf nötig.");
        }
        return pollInternal(settings, List.of(report), reportId);
    }

    public void testConnection(long unitId) {
        UnitLeitstellenMailSettings settings = settingsRepository
                .findByUnitId(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Leitstellen-Mail ist nicht konfiguriert."));
        if (settings.getImapHost() == null || settings.getImapHost().isBlank()) {
            throw new IllegalArgumentException("IMAP-Host fehlt.");
        }
        try {
            imapClient.testConnection(settings);
        } catch (Exception e) {
            throw new IllegalArgumentException("IMAP-Verbindung fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private PollResult pollInternal(
            UnitLeitstellenMailSettings settings, List<IncidentReport> candidates, Long focusReportId) {
        long unitId = settings.getUnit().getId();
        try {
            List<LeitstellenImapClient.MailMessage> mails = imapClient.fetchRecentPdfs(settings);
            int pdfCount = mails.stream().mapToInt(m -> m.pdfs().size()).sum();
            int imported = 0;
            int skipped = 0;
            int unmatched = 0;
            for (LeitstellenImapClient.MailMessage mail : mails) {
                for (LeitstellenImapClient.PdfAttachment pdf : mail.pdfs()) {
                    String sha = sha256(pdf.content());
                    if (importRepository.existsByUnitIdAndAttachmentSha256(unitId, sha)
                            || importRepository.existsByUnitIdAndMessageIdAndAttachmentName(
                                    unitId, mail.messageId(), pdf.filename())) {
                        skipped++;
                        continue;
                    }
                    var match = matcher.match(
                            settings,
                            mail,
                            pdf,
                            candidates,
                            reportId -> hasKind(reportId, LeitstellenMailKind.DEPESCHE),
                            reportId -> hasKind(reportId, LeitstellenMailKind.ABSCHLUSS));
                    if (match.isEmpty()) {
                        unmatched++;
                        continue;
                    }
                    LeitstellenMailMatcher.MatchResult hit = match.get();
                    if (focusReportId != null && hit.report().getId() != focusReportId) {
                        unmatched++;
                        continue;
                    }
                    if (alreadyComplete(hit.report().getId())) {
                        skipped++;
                        continue;
                    }
                    LeitstellenMailKind kind = hit.kind();
                    if (hasKind(hit.report().getId(), kind)) {
                        skipped++;
                        continue;
                    }
                    attachmentService.storeSystemPdf(
                            unitId, hit.report().getId(), kind.storedFilename(), pdf.content());
                    saveImport(settings.getUnit(), hit.report(), mail, pdf, sha, kind);
                    imported++;
                }
            }
            String focus = focusReportId != null ? " (nur dieser Einsatz)" : "";
            String msg = String.format(
                    Locale.GERMAN,
                    "Abruf ok%s: %d Mail(s), %d PDF-Anhang/Anhänge gefunden, %d importiert, %d übersprungen, %d ohne Treffer.",
                    focus,
                    mails.size(),
                    pdfCount,
                    imported,
                    skipped,
                    unmatched);
            return finish(settings, mails.size(), pdfCount, imported, skipped, unmatched, msg);
        } catch (Exception e) {
            log.warn("Leitstellen-Mail Abruf unit={} fehlgeschlagen: {}", unitId, e.getMessage());
            return finish(settings, 0, 0, 0, 0, 0, "Abruf fehlgeschlagen: " + e.getMessage());
        }
    }

    private List<IncidentReport> loadCandidates(long unitId, UnitLeitstellenMailSettings settings) {
        LocalDate today = LocalDate.now(ZONE);
        int lookbackDays = Math.max(1, (settings.getPollLookbackHours() + 23) / 24);
        return incidentReportRepository.findCandidatesForLeitstellenMail(
                unitId,
                List.of(IncidentReportStatus.ENTWURF, IncidentReportStatus.FREIGEGEBEN),
                today.minusDays(lookbackDays + 1L),
                today.plusDays(1),
                testModeService.isEnabled());
    }

    private boolean alreadyComplete(long reportId) {
        return hasKind(reportId, LeitstellenMailKind.DEPESCHE)
                && hasKind(reportId, LeitstellenMailKind.ABSCHLUSS);
    }

    private boolean hasKind(long reportId, LeitstellenMailKind kind) {
        if (importRepository.existsByIncidentReportIdAndKind(reportId, kind)) {
            return true;
        }
        return attachmentRepository
                .findFirstByIncidentReportIdAndFilenameIgnoreCase(reportId, kind.storedFilename())
                .isPresent();
    }

    private UnitLeitstellenMailSettings requireEnabledSettings(long unitId, boolean allowDisabledMessage) {
        UnitLeitstellenMailSettings settings = settingsRepository.findByUnitId(unitId).orElse(null);
        if (settings == null) {
            return null;
        }
        if (!settings.isEnabled()) {
            return allowDisabledMessage ? null : null;
        }
        if (settings.getImapHost() == null || settings.getImapHost().isBlank()) {
            return null;
        }
        return settings;
    }

    private PollResult disabledOrMissing(long unitId) {
        UnitLeitstellenMailSettings settings = settingsRepository.findByUnitId(unitId).orElse(null);
        if (settings == null) {
            return new PollResult(0, 0, 0, 0, 0, "Leitstellen-Mail ist nicht konfiguriert.");
        }
        if (!settings.isEnabled()) {
            return finish(settings, 0, 0, 0, 0, 0, "Abruf deaktiviert.");
        }
        return finish(settings, 0, 0, 0, 0, 0, "IMAP-Host fehlt.");
    }

    private void saveImport(
            Unit unit,
            IncidentReport report,
            LeitstellenImapClient.MailMessage mail,
            LeitstellenImapClient.PdfAttachment pdf,
            String sha,
            LeitstellenMailKind kind) {
        LeitstellenMailImport row = new LeitstellenMailImport();
        row.setUnit(unit);
        row.setIncidentReport(report);
        row.setMessageId(mail.messageId());
        row.setImapUid(mail.uid());
        row.setAttachmentName(pdf.filename());
        row.setAttachmentSha256(sha);
        row.setKind(kind);
        row.setStoredFilename(kind.storedFilename());
        row.setCreatedAt(Instant.now());
        importRepository.save(row);
    }

    private PollResult finish(
            UnitLeitstellenMailSettings settings,
            int fetched,
            int pdfs,
            int imported,
            int skipped,
            int unmatched,
            String message) {
        settings.setLastPollAt(Instant.now());
        settings.setLastPollMessage(trimMessage(message));
        settings.setUpdatedAt(Instant.now());
        settingsRepository.save(settings);
        return new PollResult(fetched, pdfs, imported, skipped, unmatched, message);
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception e) {
            return HexFormat.of().formatHex(Integer.toHexString(content.length).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String trimMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 512 ? message : message.substring(0, 509) + "...";
    }
}
