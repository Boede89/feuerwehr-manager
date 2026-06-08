package de.feuerwehr.manager.mail;

import de.feuerwehr.manager.unit.UnitSmtpAccount;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UnitMailService {

    private final AccountMailService accountMailService;

    public boolean canSendForUnit(long unitId) {
        return accountMailService.canSendMailForUnit(unitId);
    }

    /** @return leer bei Erfolg, sonst Fehlermeldung */
    public Optional<String> sendHtmlMail(long unitId, String toEmail, String subject, String htmlBody) {
        if (toEmail == null || toEmail.isBlank()) {
            return Optional.of("Keine E-Mail-Adresse hinterlegt.");
        }
        Optional<UnitSmtpAccount> smtp = accountMailService.resolveDefaultUnitSmtp(unitId);
        if (smtp.isEmpty()) {
            return Optional.of("SMTP der Einheit ist nicht konfiguriert (Admin → Einheit → Schnittstellen).");
        }
        try {
            UnitSmtpAccount account = smtp.get();
            JavaMailSenderImpl sender = SmtpMailService.buildSender(
                    account.getSmtpHost(),
                    account.getSmtpPort(),
                    account.getSmtpUsername(),
                    account.getSmtpPassword(),
                    account.getSmtpEncryption());
            var message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            String fromName = account.getSmtpFromName() != null && !account.getSmtpFromName().isBlank()
                    ? account.getSmtpFromName()
                    : "Feuerwehr-Manager";
            helper.setFrom(account.getSmtpFromEmail(), fromName);
            helper.setTo(toEmail.trim());
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            sender.send(message);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of("E-Mail konnte nicht gesendet werden: " + e.getMessage());
        }
    }
}
