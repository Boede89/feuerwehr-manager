package de.feuerwehr.manager.support;

import de.feuerwehr.manager.mail.AccountMailService;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import de.feuerwehr.manager.web.dto.BugReportRequest;
import de.feuerwehr.manager.web.dto.BugReportResult;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BugReportService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY).withZone(ZONE);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final AccountMailService accountMailService;
    private final GlobalSettingsService globalSettingsService;

    public BugReportResult submit(BugReportRequest request) {
        String reporterName = normalize(request != null ? request.reporterName() : null, 120);
        String reporterEmail = normalize(request != null ? request.reporterEmail() : null, 255);
        String area = normalize(request != null ? request.area() : null, 80);
        String description = normalize(request != null ? request.description() : null, 4000);
        String pageUrl = normalize(request != null ? request.pageUrl() : null, 500);

        if (reporterName.isBlank()) {
            return BugReportResult.fail("Bitte Ihren Namen angeben.");
        }
        if (reporterEmail.isBlank()) {
            return BugReportResult.fail("Bitte Ihre E-Mail-Adresse angeben.");
        }
        if (!EMAIL_PATTERN.matcher(reporterEmail).matches()) {
            return BugReportResult.fail("Bitte eine gültige E-Mail-Adresse angeben.");
        }
        if (area.isBlank()) {
            return BugReportResult.fail("Bitte einen Bereich auswählen.");
        }
        if (description.length() < 10) {
            return BugReportResult.fail("Bitte beschreiben Sie den Fehler etwas ausführlicher (mindestens 10 Zeichen).");
        }
        if (!accountMailService.canSendGlobalMail()) {
            return BugReportResult.fail(
                    "Fehlermeldungen können derzeit nicht versendet werden (globaler SMTP nicht konfiguriert).");
        }

        List<User> recipients = userRepository.findActiveSuperAdminsWithEmail();
        if (recipients.isEmpty()) {
            return BugReportResult.fail("Kein Superadmin mit E-Mail-Adresse hinterlegt.");
        }

        String ffName = globalSettingsService.get().getFfName();
        String appName = ffName != null && !ffName.isBlank() ? ffName.trim() : "Feuerwehr-Manager";
        String subject = appName + " – Fehlermeldung"
                + (area.isBlank() ? "" : " (" + area + ")");
        String body = buildBody(appName, reporterName, reporterEmail, area, description, pageUrl);

        int sent = 0;
        String lastError = null;
        for (User recipient : recipients) {
            var error = accountMailService.sendGlobalPlainMail(recipient.getLoginEmail().trim(), subject, body);
            if (error.isEmpty()) {
                sent++;
            } else {
                lastError = error.get();
            }
        }
        if (sent == 0) {
            return BugReportResult.fail(lastError != null
                    ? lastError
                    : "Die Fehlermeldung konnte nicht versendet werden.");
        }
        return BugReportResult.ok("Vielen Dank. Ihre Fehlermeldung wurde an den Administrator übermittelt.");
    }

    private static String buildBody(
            String appName,
            String reporterName,
            String reporterEmail,
            String area,
            String description,
            String pageUrl) {
        StringBuilder body = new StringBuilder();
        body.append("Neue Fehlermeldung über die Startseite von ").append(appName).append("\n\n");
        body.append("Eingegangen: ").append(TIMESTAMP.format(Instant.now())).append("\n");
        body.append("Name: ").append(reporterName).append("\n");
        body.append("E-Mail: ").append(reporterEmail).append("\n");
        body.append("Bereich: ").append(area).append("\n");
        if (!pageUrl.isBlank()) {
            body.append("Seite: ").append(pageUrl).append("\n");
        }
        body.append("\nBeschreibung:\n").append(description).append("\n");
        return body.toString();
    }

    private static String normalize(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }
}
