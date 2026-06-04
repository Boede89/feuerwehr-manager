package de.feuerwehr.manager.mail;

import de.feuerwehr.manager.settings.ApplicationSettings;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.user.User;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountMailService {

    private final GlobalSettingsService globalSettingsService;

    public boolean canSendMail() {
        ApplicationSettings settings = globalSettingsService.get();
        if (settings.getSmtpHost() == null || settings.getSmtpHost().isBlank()) {
            return false;
        }
        if (settings.getSmtpFromEmail() == null || settings.getSmtpFromEmail().isBlank()) {
            return false;
        }
        return true;
    }

    /** @return leer bei Erfolg, sonst Hinweistext */
    public Optional<String> sendPasswordNotification(User user, String plainPassword, boolean passwordReset) {
        String to = user.getLoginEmail();
        if (to == null || to.isBlank()) {
            return Optional.of("Keine E-Mail-Adresse am Benutzer hinterlegt.");
        }
        if (!canSendMail()) {
            return Optional.of("SMTP ist nicht konfiguriert (Admin → Global → Schnittstellen).");
        }
        try {
            ApplicationSettings settings = globalSettingsService.get();
            JavaMailSenderImpl sender = SmtpMailService.buildSender(
                    settings.getSmtpHost(),
                    settings.getSmtpPort(),
                    settings.getSmtpUsername(),
                    settings.getSmtpPassword(),
                    settings.getSmtpEncryption());
            var message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            String fromName = settings.getSmtpFromName() != null ? settings.getSmtpFromName() : "Feuerwehr-Manager";
            helper.setFrom(settings.getSmtpFromEmail(), fromName);
            helper.setTo(to);
            String ffName = settings.getFfName() != null ? settings.getFfName() : "Feuerwehr-Manager";
            if (passwordReset) {
                helper.setSubject(ffName + " – Neues Passwort");
                helper.setText(buildResetBody(user, plainPassword, ffName, settings), false);
            } else {
                helper.setSubject(ffName + " – Zugangsdaten");
                helper.setText(buildWelcomeBody(user, plainPassword, ffName, settings), false);
            }
            sender.send(message);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of("E-Mail konnte nicht gesendet werden: " + e.getMessage());
        }
    }

    private static String buildWelcomeBody(
            User user, String plainPassword, String ffName, ApplicationSettings settings) {
        String appUrl = settings.getAppUrl() != null ? settings.getAppUrl() : "";
        StringBuilder body = new StringBuilder();
        body.append("Guten Tag ").append(user.getDisplayName()).append(",\n\n");
        body.append("für Sie wurde ein Zugang bei ").append(ffName).append(" angelegt.\n\n");
        body.append("Benutzername: ").append(user.getUsername()).append("\n");
        body.append("Passwort: ").append(plainPassword).append("\n");
        if (!appUrl.isBlank()) {
            body.append("Anmeldung: ").append(appUrl).append("\n");
        }
        body.append("\nBitte ändern Sie das Passwort nach der ersten Anmeldung.\n");
        return body.toString();
    }

    private static String buildResetBody(
            User user, String plainPassword, String ffName, ApplicationSettings settings) {
        String appUrl = settings.getAppUrl() != null ? settings.getAppUrl() : "";
        StringBuilder body = new StringBuilder();
        body.append("Guten Tag ").append(user.getDisplayName()).append(",\n\n");
        body.append("Ihr Passwort bei ").append(ffName).append(" wurde zurückgesetzt.\n\n");
        body.append("Benutzername: ").append(user.getUsername()).append("\n");
        body.append("Neues Passwort: ").append(plainPassword).append("\n");
        if (!appUrl.isBlank()) {
            body.append("Anmeldung: ").append(appUrl).append("\n");
        }
        return body.toString();
    }
}
