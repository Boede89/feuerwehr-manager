package de.feuerwehr.manager.leitstellen;

import de.feuerwehr.manager.berichte.EinsatzberichtAttachmentService;
import de.feuerwehr.manager.berichte.IncidentReport;
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
    private final EinsatzberichtAttachmentService attachmentService;
    private final LeitstellenImapClient imapClient;
    private final LeitstellenMailMatcher matcher;
    private final TestModeService testModeService;

    public record PollResult(int fetchedMails, int importedAttachments, int skipped, int unmatched, String message) {}

    @Transactional
    public PollResult pollUnit(long unitId) {
        UnitLeitstellenMailSettings settings = settingsRepository
                .findByUnitId(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Leitstellen-Mail ist nicht konfiguriert."));
        if (!settings.isEnabled()) {
            return finish(settings, 0, 0, 0, 0, "Abruf deaktiviert.");
        }
        if (settings.getImapHost() == null || settings.getImapHost().isBlank()) {
            return finish(settings, 0, 0, 0, 0, "IMAP-Host fehlt.");
        }
        try {
            List<LeitstellenImapClient.MailMessage> mails = imapClient.fetchRecentPdfs(settings);
            LocalDate today = LocalDate.now(ZONE);
            int lookbackDays = Math.max(1, (settings.getPollLookbackHours() + 23) / 24);
            List<IncidentReport> candidates = incidentReportRepository.findCandidatesForLeitstellenMail(
                    unitId,
                    List.of(IncidentReportStatus.ENTWURF, IncidentReportStatus.FREIGEGEBEN),
                    today.minusDays(lookbackDays + 1L),
                    today.plusDays(1),
                    testModeService.isEnabled());

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
                    var match = matcher.match(settings, mail, pdf, candidates);
                    if (match.isEmpty()) {
                        unmatched++;
                        continue;
                    }
                    LeitstellenMailMatcher.MatchResult hit = match.get();
                    LeitstellenMailKind kind = refineKind(settings, hit.report().getId(), mail, pdf);
                    attachmentService.storeSystemPdf(
                            unitId, hit.report().getId(), kind.storedFilename(), pdf.content());
                    saveImport(settings.getUnit(), hit.report(), mail, pdf, sha, kind);
                    imported++;
                }
            }
            String msg = String.format(
                    Locale.GERMAN,
                    "Abruf ok: %d Mail(s), %d Anhang/Anhänge importiert, %d übersprungen, %d ohne Treffer (werden erneut versucht).",
                    mails.size(),
                    imported,
                    skipped,
                    unmatched);
            return finish(settings, mails.size(), imported, skipped, unmatched, msg);
        } catch (Exception e) {
            log.warn("Leitstellen-Mail Abruf unit={} fehlgeschlagen: {}", unitId, e.getMessage());
            return finish(settings, 0, 0, 0, 0, "Abruf fehlgeschlagen: " + e.getMessage());
        }
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

    private LeitstellenMailKind refineKind(
            UnitLeitstellenMailSettings settings,
            long reportId,
            LeitstellenImapClient.MailMessage mail,
            LeitstellenImapClient.PdfAttachment pdf) {
        String haystack = (nullToEmpty(mail.subject()) + " " + nullToEmpty(pdf.filename())).toLowerCase(Locale.GERMAN);
        boolean explicitAbschluss = containsKeyword(haystack, settings.getAbschlussKeywords());
        boolean explicitDepesche = containsKeyword(haystack, settings.getDepescheKeywords());
        if (explicitAbschluss && !explicitDepesche) {
            return LeitstellenMailKind.ABSCHLUSS;
        }
        if (explicitDepesche && !explicitAbschluss) {
            return LeitstellenMailKind.DEPESCHE;
        }
        boolean hasDepesche = importRepository.existsByIncidentReportIdAndKind(reportId, LeitstellenMailKind.DEPESCHE);
        boolean hasAbschluss =
                importRepository.existsByIncidentReportIdAndKind(reportId, LeitstellenMailKind.ABSCHLUSS);
        if (hasDepesche && !hasAbschluss) {
            return LeitstellenMailKind.ABSCHLUSS;
        }
        return LeitstellenMailKind.DEPESCHE;
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
            int imported,
            int skipped,
            int unmatched,
            String message) {
        settings.setLastPollAt(Instant.now());
        settings.setLastPollMessage(trimMessage(message));
        settings.setUpdatedAt(Instant.now());
        settingsRepository.save(settings);
        return new PollResult(fetched, imported, skipped, unmatched, message);
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception e) {
            return HexFormat.of().formatHex(Integer.toHexString(content.length).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean containsKeyword(String haystack, String keywordsCsv) {
        if (keywordsCsv == null || keywordsCsv.isBlank()) {
            return false;
        }
        for (String raw : keywordsCsv.split(",")) {
            String keyword = raw.trim().toLowerCase(Locale.GERMAN);
            if (!keyword.isBlank() && haystack.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String trimMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 512 ? message : message.substring(0, 509) + "...";
    }
}
