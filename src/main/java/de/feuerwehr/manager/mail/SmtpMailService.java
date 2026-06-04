package de.feuerwehr.manager.mail;

import de.feuerwehr.manager.settings.ApplicationSettings;
import de.feuerwehr.manager.unit.UnitSmtpAccount;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmtpMailService {

    /** @return leer bei Erfolg, sonst Fehlermeldung */
    public Optional<String> sendTestMail(ApplicationSettings settings, String toEmail) {
        if (settings.getSmtpHost() == null || settings.getSmtpHost().isBlank()) {
            return Optional.of("SMTP-Host fehlt.");
        }
        if (settings.getSmtpFromEmail() == null || settings.getSmtpFromEmail().isBlank()) {
            return Optional.of("Absender-E-Mail fehlt.");
        }
        if (toEmail == null || toEmail.isBlank()) {
            return Optional.of("Keine Empfänger-E-Mail (bitte Login-E-Mail am Benutzer hinterlegen).");
        }
        try {
            JavaMailSenderImpl sender = buildSender(
                    settings.getSmtpHost(),
                    settings.getSmtpPort(),
                    settings.getSmtpUsername(),
                    settings.getSmtpPassword(),
                    settings.getSmtpEncryption());
            sendTestMessage(sender, settings.getSmtpFromEmail(), settings.getSmtpFromName(), toEmail.trim());
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of("E-Mail-Versand fehlgeschlagen: " + e.getMessage());
        }
    }

    /** Einheits-SMTP-Konto, falls Host gesetzt; sonst global. */
    public Optional<String> sendTestMail(UnitSmtpAccount unitSmtp, ApplicationSettings global, String toEmail) {
        if (unitSmtp != null && unitSmtp.getSmtpHost() != null && !unitSmtp.getSmtpHost().isBlank()) {
            if (unitSmtp.getSmtpFromEmail() == null || unitSmtp.getSmtpFromEmail().isBlank()) {
                return Optional.of("Absender-E-Mail der Einheit fehlt.");
            }
            try {
                JavaMailSenderImpl sender = buildSender(
                        unitSmtp.getSmtpHost(),
                        unitSmtp.getSmtpPort(),
                        unitSmtp.getSmtpUsername(),
                        unitSmtp.getSmtpPassword(),
                        unitSmtp.getSmtpEncryption());
                sendTestMessage(
                        sender, unitSmtp.getSmtpFromEmail(), unitSmtp.getSmtpFromName(), toEmail.trim());
                return Optional.empty();
            } catch (Exception e) {
                return Optional.of("E-Mail-Versand fehlgeschlagen: " + e.getMessage());
            }
        }
        return sendTestMail(global, toEmail);
    }

    static JavaMailSenderImpl buildSender(
            String host, Integer port, String username, String password, String encryption) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port != null && port > 0 ? port : 587);
        if (username != null && !username.isBlank()) {
            sender.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            sender.setPassword(password);
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", username != null && !username.isBlank());
        String enc = encryption != null ? encryption : "TLS";
        if ("SSL".equalsIgnoreCase(enc)) {
            props.put("mail.smtp.ssl.enable", "true");
        } else if ("TLS".equalsIgnoreCase(enc)) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        return sender;
    }

    private static void sendTestMessage(
            JavaMailSenderImpl sender, String fromEmail, String fromName, String toEmail) throws Exception {
        var message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        String name = fromName != null && !fromName.isBlank() ? fromName : "Feuerwehr-Manager";
        helper.setFrom(fromEmail, name);
        helper.setTo(toEmail);
        helper.setSubject("Feuerwehr-Manager – SMTP Test");
        helper.setText(
                "Diese E-Mail bestätigt, dass die SMTP-Konfiguration in Feuerwehr-Manager funktioniert.",
                false);
        sender.send(message);
    }
}
