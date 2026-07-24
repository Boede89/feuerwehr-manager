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
    /** Manueller Abruf: mindestens 30 Tage zurück (Catch-up für vorhandene Mails). */
    private static final int MANUAL_CATCHUP_LOOKBACK_HOURS = 24 * 30;
    /** Manueller Abruf: größeres Zuordnungsfenster, falls FAX etwas später kam. */
    private static final int MANUAL_CATCHUP_MATCH_WINDOW_HOURS = 72;

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
        return pollUnit(unitId, false);
    }

    /**
     * @param catchUp true = manueller Abruf: längerer Lookback, kein Abbruch vor IMAP
     */
    @Transactional
    public PollResult pollUnit(long unitId, boolean catchUp) {
        UnitLeitstellenMailSettings settings = requireEnabledSettings(unitId);
        if (settings == null) {
            return disabledOrMissing(unitId);
        }
        int lookbackHours = effectiveLookbackHours(settings, catchUp);
        int matchWindowHours = effectiveMatchWindowHours(settings, catchUp);
        cleanupOrphanImports(unitId);
        List<IncidentReport> allInWindow = loadCandidates(unitId, lookbackHours);
        return pollInternal(
                settings, allInWindow, null, lookbackHours, matchWindowHours, allInWindow.size(), catchUp);
    }

    /**
     * Einmaliger Abruf nur für einen Einsatzbericht (auch freigegeben), sofern Dateien fehlen.
     * Zuordnung nur, wenn dieser Bericht unter allen Einsätzen im Zeitraum der beste Treffer ist
     * (verhindert Zuordnung von Mails eines anderen Einsatzes).
     */
    @Transactional
    public PollResult pollForReport(long unitId, long reportId) {
        UnitLeitstellenMailSettings settings = requireEnabledSettings(unitId);
        if (settings == null) {
            return disabledOrMissing(unitId);
        }
        IncidentReport report = incidentReportRepository
                .findById(reportId)
                .filter(r -> r.getUnit() != null && r.getUnit().getId() == unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einsatzbericht nicht gefunden."));
        if (report.getStatus() == IncidentReportStatus.ARCHIVIERT) {
            return finish(settings, 0, 0, 0, 0, 0, "Archivierte Berichte werden nicht ergänzt.");
        }
        cleanupOrphanImports(unitId);
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
        int lookbackHours = effectiveLookbackHours(settings, true);
        // Strenges Fenster: konfigurierter Wert, nicht das erweiterte Catch-up-Fenster
        int matchWindowHours = Math.max(1, settings.getMatchWindowHours());
        List<IncidentReport> neighbors = loadCandidates(unitId, lookbackHours);
        if (neighbors.stream().noneMatch(r -> r.getId() == reportId)) {
            neighbors = new java.util.ArrayList<>(neighbors);
            neighbors.add(report);
        }
        return pollInternal(settings, neighbors, reportId, lookbackHours, matchWindowHours, neighbors.size(), true);
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
            UnitLeitstellenMailSettings settings,
            List<IncidentReport> candidates,
            Long focusReportId,
            int lookbackHours,
            int matchWindowHours,
            int reportsInWindow,
            boolean catchUp) {
        long unitId = settings.getUnit().getId();
        UnitLeitstellenMailSettings matchSettings = copyForMatch(settings, matchWindowHours);
        try {
            List<LeitstellenImapClient.MailMessage> mails = imapClient.fetchRecentPdfs(settings, lookbackHours);
            int pdfCount = mails.stream().mapToInt(m -> m.pdfs().size()).sum();

            if (candidates.isEmpty()) {
                String reason = String.format(
                        Locale.GERMAN,
                        "Keine Einsatzberichte (Entwurf/freigegeben) in den letzten %d Tagen. "
                                + "Mails geprüft: %d mit %d PDF(s). Lookback in den Einstellungen erhöhen?",
                        Math.max(1, (lookbackHours + 23) / 24),
                        mails.size(),
                        pdfCount);
                return finish(settings, mails.size(), pdfCount, 0, 0, 0, reason);
            }
            long incompleteCount = candidates.stream().filter(r -> !alreadyComplete(r.getId())).count();
            if (incompleteCount == 0) {
                String reason = String.format(
                        Locale.GERMAN,
                        "Für alle %d Berichte im Zeitraum liegen Depeche und Abschlussbericht bereits vor. "
                                + "Mails geprüft: %d mit %d PDF(s).",
                        reportsInWindow,
                        mails.size(),
                        pdfCount);
                return finish(settings, mails.size(), pdfCount, 0, 0, 0, reason);
            }

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
                            matchSettings,
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
            String catchUpHint = catchUp ? " [Catch-up " + lookbackHours + "h]" : "";
            String msg = String.format(
                    Locale.GERMAN,
                    "Abruf ok%s%s: %d Mail(s), %d PDF(s), %d importiert, %d übersprungen, %d ohne Treffer "
                            + "(%d Berichte ohne vollständige Anhänge).",
                    focus,
                    catchUpHint,
                    mails.size(),
                    pdfCount,
                    imported,
                    skipped,
                    unmatched,
                    incompleteCount);
            return finish(settings, mails.size(), pdfCount, imported, skipped, unmatched, msg);
        } catch (Exception e) {
            log.warn("Leitstellen-Mail Abruf unit={} fehlgeschlagen: {}", unitId, e.getMessage());
            return finish(settings, 0, 0, 0, 0, 0, "Abruf fehlgeschlagen: " + e.getMessage());
        }
    }

    private List<IncidentReport> loadCandidates(long unitId, int lookbackHours) {
        LocalDate today = LocalDate.now(ZONE);
        int lookbackDays = Math.max(1, (lookbackHours + 23) / 24);
        return incidentReportRepository.findCandidatesForLeitstellenMail(
                unitId,
                List.of(IncidentReportStatus.ENTWURF, IncidentReportStatus.FREIGEGEBEN),
                today.minusDays(lookbackDays),
                today.plusDays(1),
                testModeService.isEnabled());
    }

    private static int effectiveLookbackHours(UnitLeitstellenMailSettings settings, boolean catchUp) {
        int configured = Math.max(1, settings.getPollLookbackHours());
        if (catchUp) {
            return Math.max(configured, MANUAL_CATCHUP_LOOKBACK_HOURS);
        }
        return configured;
    }

    private static int effectiveMatchWindowHours(UnitLeitstellenMailSettings settings, boolean catchUp) {
        int configured = Math.max(1, settings.getMatchWindowHours());
        if (catchUp) {
            return Math.max(configured, MANUAL_CATCHUP_MATCH_WINDOW_HOURS);
        }
        return configured;
    }

    private static UnitLeitstellenMailSettings copyForMatch(
            UnitLeitstellenMailSettings source, int matchWindowHours) {
        UnitLeitstellenMailSettings copy = new UnitLeitstellenMailSettings();
        copy.setDepescheKeywords(source.getDepescheKeywords());
        copy.setAbschlussKeywords(source.getAbschlussKeywords());
        copy.setMatchWindowHours(matchWindowHours);
        return copy;
    }

    private boolean alreadyComplete(long reportId) {
        return hasKind(reportId, LeitstellenMailKind.DEPESCHE)
                && hasKind(reportId, LeitstellenMailKind.ABSCHLUSS);
    }

    private boolean hasKind(long reportId, LeitstellenMailKind kind) {
        // Nur echte Anhänge zählen — verwaiste Import-Zeilen (ohne PDF) dürfen Abruf nicht blockieren
        return attachmentRepository
                .findFirstByIncidentReportIdAndFilenameIgnoreCase(reportId, kind.storedFilename())
                .isPresent();
    }

    /** Entfernt Import-Einträge, deren PDF am Bericht fehlt (z. B. nach manuellem Löschen vor Cleanup-Fix). */
    private void cleanupOrphanImports(long unitId) {
        for (LeitstellenMailImport row : importRepository.findByUnitId(unitId)) {
            if (row.getIncidentReport() == null || row.getKind() == null) {
                continue;
            }
            boolean filePresent = attachmentRepository
                    .findFirstByIncidentReportIdAndFilenameIgnoreCase(
                            row.getIncidentReport().getId(), row.getKind().storedFilename())
                    .isPresent();
            if (!filePresent) {
                importRepository.delete(row);
            }
        }
    }

    private UnitLeitstellenMailSettings requireEnabledSettings(long unitId) {
        UnitLeitstellenMailSettings settings = settingsRepository.findByUnitId(unitId).orElse(null);
        if (settings == null || !settings.isEnabled()) {
            return null;
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
