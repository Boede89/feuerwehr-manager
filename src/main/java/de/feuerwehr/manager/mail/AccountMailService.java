package de.feuerwehr.manager.mail;

import de.feuerwehr.manager.settings.ApplicationSettings;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.unit.UnitAdminService;
import de.feuerwehr.manager.unit.UnitSmtpAccount;
import de.feuerwehr.manager.user.User;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountMailService {

    private final GlobalSettingsService globalSettingsService;
    private final UnitAdminService unitAdminService;

    /** Einheits-SMTP (Standard für Kontakt-Mails). */
    public boolean canSendMailForUnit(long unitId) {
        return resolveDefaultUnitSmtp(unitId).isPresent();
    }

    /** Globaler SMTP (nur für ausdrücklich globale Funktionen). */
    public boolean canSendGlobalMail() {
        ApplicationSettings settings = globalSettingsService.get();
        if (settings.getSmtpHost() == null || settings.getSmtpHost().isBlank()) {
            return false;
        }
        return settings.getSmtpFromEmail() != null && !settings.getSmtpFromEmail().isBlank();
    }

    public Optional<UnitSmtpAccount> resolveDefaultUnitSmtp(long unitId) {
        List<UnitSmtpAccount> accounts = unitAdminService.listSmtpAccounts(unitId);
        for (UnitSmtpAccount account : accounts) {
            if (isUnitSmtpReady(account)) {
                return Optional.of(account);
            }
        }
        return Optional.empty();
    }

    /**
     * Passwort-Benachrichtigung über Einheits-SMTP (kein Fallback auf global).
     *
     * @return leer bei Erfolg, sonst Hinweistext
     */
    public Optional<String> sendPasswordNotification(
            User user, long unitId, String plainPassword, boolean passwordReset) {
        String to = user.getLoginEmail();
        if (to == null || to.isBlank()) {
            return Optional.of("Keine E-Mail-Adresse am Benutzer hinterlegt.");
        }
        Optional<UnitSmtpAccount> smtp = resolveDefaultUnitSmtp(unitId);
        if (smtp.isEmpty()) {
            return Optional.of("SMTP der Einheit ist nicht konfiguriert (Admin → Einheit → Schnittstellen).");
        }
        return sendWithUnitSmtp(user, smtp.get(), plainPassword, passwordReset);
    }

    /**
     * Passwort-Benachrichtigung über globalen SMTP (nur für ausdrücklich globale Funktionen).
     *
     * @return leer bei Erfolg, sonst Hinweistext
     */
    public Optional<String> sendPasswordNotificationGlobal(
            User user, String plainPassword, boolean passwordReset) {
        String to = user.getLoginEmail();
        if (to == null || to.isBlank()) {
            return Optional.of("Keine E-Mail-Adresse am Benutzer hinterlegt.");
        }
        if (!canSendGlobalMail()) {
            return Optional.of("Globaler SMTP ist nicht konfiguriert (Admin → Global → Schnittstellen).");
        }
        try {
            ApplicationSettings settings = globalSettingsService.get();
            JavaMailSenderImpl sender = SmtpMailService.buildSender(
                    settings.getSmtpHost(),
                    settings.getSmtpPort(),
                    settings.getSmtpUsername(),
                    settings.getSmtpPassword(),
                    settings.getSmtpEncryption());
            String ffName = settings.getFfName() != null ? settings.getFfName() : "Feuerwehr-Manager";
            sendMessage(sender, settings.getSmtpFromEmail(), settings.getSmtpFromName(), user, to, plainPassword, passwordReset, ffName, settings.getAppUrl());
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of("E-Mail konnte nicht gesendet werden: " + e.getMessage());
        }
    }

    private Optional<String> sendWithUnitSmtp(
            User user, UnitSmtpAccount smtp, String plainPassword, boolean passwordReset) {
        try {
            JavaMailSenderImpl sender = SmtpMailService.buildSender(
                    smtp.getSmtpHost(),
                    smtp.getSmtpPort(),
                    smtp.getSmtpUsername(),
                    smtp.getSmtpPassword(),
                    smtp.getSmtpEncryption());
            String ffName = resolveUnitDisplayName(user);
            String appUrl = globalSettingsService.get().getAppUrl();
            sendMessage(
                    sender,
                    smtp.getSmtpFromEmail(),
                    smtp.getSmtpFromName(),
                    user,
                    user.getLoginEmail(),
                    plainPassword,
                    passwordReset,
                    ffName,
                    appUrl);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of("E-Mail konnte nicht gesendet werden: " + e.getMessage());
        }
    }

    private static boolean isUnitSmtpReady(UnitSmtpAccount account) {
        return account.getSmtpHost() != null
                && !account.getSmtpHost().isBlank()
                && account.getSmtpFromEmail() != null
                && !account.getSmtpFromEmail().isBlank();
    }

    private static String resolveUnitDisplayName(User user) {
        if (user.getUnit() != null && user.getUnit().getName() != null && !user.getUnit().getName().isBlank()) {
            return user.getUnit().getName().trim();
        }
        return "Feuerwehr-Manager";
    }

    private static void sendMessage(
            JavaMailSenderImpl sender,
            String fromEmail,
            String fromName,
            User user,
            String to,
            String plainPassword,
            boolean passwordReset,
            String ffName,
            String appUrl)
            throws Exception {
        var message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        String senderName = fromName != null && !fromName.isBlank() ? fromName : ffName;
        helper.setFrom(fromEmail, senderName);
        helper.setTo(to.trim());
        if (passwordReset) {
            helper.setSubject(ffName + " – Neues Passwort");
            helper.setText(buildResetBody(user, plainPassword, ffName, appUrl), false);
        } else {
            helper.setSubject(ffName + " – Zugangsdaten");
            helper.setText(buildWelcomeBody(user, plainPassword, ffName, appUrl), false);
        }
        sender.send(message);
    }

    private static String buildWelcomeBody(User user, String plainPassword, String ffName, String appUrl) {
        String url = appUrl != null ? appUrl : "";
        StringBuilder body = new StringBuilder();
        body.append("Guten Tag ").append(user.getDisplayName()).append(",\n\n");
        body.append("für Sie wurde ein Zugang bei ").append(ffName).append(" angelegt.\n\n");
        body.append("Benutzername: ").append(user.getUsername()).append("\n");
        body.append("Passwort: ").append(plainPassword).append("\n");
        if (!url.isBlank()) {
            body.append("Anmeldung: ").append(url).append("\n");
        }
        body.append("\nBitte ändern Sie das Passwort nach der ersten Anmeldung.\n");
        return body.toString();
    }

    private static String buildResetBody(User user, String plainPassword, String ffName, String appUrl) {
        String url = appUrl != null ? appUrl : "";
        StringBuilder body = new StringBuilder();
        body.append("Guten Tag ").append(user.getDisplayName()).append(",\n\n");
        body.append("Ihr Passwort bei ").append(ffName).append(" wurde zurückgesetzt.\n\n");
        body.append("Benutzername: ").append(user.getUsername()).append("\n");
        body.append("Neues Passwort: ").append(plainPassword).append("\n");
        if (!url.isBlank()) {
            body.append("Anmeldung: ").append(url).append("\n");
        }
        return body.toString();
    }
}
