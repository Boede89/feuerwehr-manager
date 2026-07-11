package de.feuerwehr.manager.mail;

import de.feuerwehr.manager.unit.UnitSmtpAccount;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
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
        return sendHtmlMail(unitId, toEmail, List.of(), subject, htmlBody);
    }

    /** @return leer bei Erfolg, sonst Fehlermeldung */
    public Optional<String> sendHtmlMail(
            long unitId, String toEmail, List<String> ccEmails, String subject, String htmlBody) {
        return sendHtmlMail(unitId, toEmail, ccEmails, subject, htmlBody, null, null);
    }

    /** @return leer bei Erfolg, sonst Fehlermeldung */
    public Optional<String> sendHtmlMail(
            long unitId,
            String toEmail,
            List<String> ccEmails,
            String subject,
            String htmlBody,
            String attachmentFilename,
            byte[] attachmentData) {
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
            boolean multipart = attachmentFilename != null && attachmentData != null && attachmentData.length > 0;
            MimeMessageHelper helper = new MimeMessageHelper(message, multipart, StandardCharsets.UTF_8.name());
            String fromName = account.getSmtpFromName() != null && !account.getSmtpFromName().isBlank()
                    ? account.getSmtpFromName()
                    : "Feuerwehr-Manager";
            helper.setFrom(account.getSmtpFromEmail(), fromName);
            helper.setTo(toEmail.trim());
            List<String> cc = normalizeCc(ccEmails, toEmail);
            if (!cc.isEmpty()) {
                helper.setCc(cc.toArray(String[]::new));
            }
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            if (multipart) {
                helper.addAttachment(attachmentFilename, new ByteArrayResource(attachmentData), "application/pdf");
            }
            sender.send(message);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of("E-Mail konnte nicht gesendet werden: " + e.getMessage());
        }
    }

    private static List<String> normalizeCc(List<String> ccEmails, String toEmail) {
        if (ccEmails == null || ccEmails.isEmpty()) {
            return List.of();
        }
        String to = toEmail != null ? toEmail.trim().toLowerCase() : "";
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String email : ccEmails) {
            if (email == null || email.isBlank()) {
                continue;
            }
            String trimmed = email.trim();
            if (!trimmed.equalsIgnoreCase(to)) {
                unique.add(trimmed);
            }
        }
        return new ArrayList<>(unique);
    }
}
