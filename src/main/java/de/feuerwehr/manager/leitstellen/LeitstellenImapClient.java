package de.feuerwehr.manager.leitstellen;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.springframework.stereotype.Component;

@Component
public class LeitstellenImapClient {

    public record PdfAttachment(String filename, byte[] content) {}

    public record MailMessage(
            String messageId,
            Long uid,
            String subject,
            String fromAddress,
            Instant receivedAt,
            List<PdfAttachment> pdfs) {}

    public List<MailMessage> fetchRecentPdfs(UnitLeitstellenMailSettings settings) throws MessagingException {
        if (settings.getImapHost() == null || settings.getImapHost().isBlank()) {
            throw new IllegalArgumentException("IMAP-Host fehlt.");
        }
        Properties props = buildProperties(settings);
        Session session = Session.getInstance(props);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(protocol(settings));
            store.connect(
                    settings.getImapHost().trim(),
                    settings.getImapUsername() != null ? settings.getImapUsername().trim() : null,
                    settings.getImapPassword());
            String folderName =
                    settings.getImapFolder() != null && !settings.getImapFolder().isBlank()
                            ? settings.getImapFolder().trim()
                            : "INBOX";
            folder = store.getFolder(folderName);
            folder.open(Folder.READ_WRITE);
            int lookbackHours = Math.max(1, settings.getPollLookbackHours());
            Date since = Date.from(Instant.now().minusSeconds(lookbackHours * 3600L));
            SearchTerm term = new ReceivedDateTerm(ComparisonTerm.GE, since);
            Message[] messages = folder.search(term);
            UIDFolder uidFolder = folder instanceof UIDFolder uf ? uf : null;
            List<MailMessage> result = new ArrayList<>();
            for (Message message : messages) {
                Instant received = receivedAt(message);
                String subject = safeSubject(message);
                String from = firstFrom(message);
                if (!matchesFilters(settings, subject, from)) {
                    continue;
                }
                List<PdfAttachment> pdfs = extractPdfs(message);
                if (pdfs.isEmpty()) {
                    continue;
                }
                String messageId = resolveMessageId(message);
                Long uid = uidFolder != null ? uidFolder.getUID(message) : null;
                result.add(new MailMessage(messageId, uid, subject, from, received, pdfs));
            }
            return result;
        } finally {
            closeQuietly(folder, store);
        }
    }

    public void testConnection(UnitLeitstellenMailSettings settings) throws MessagingException {
        Properties props = buildProperties(settings);
        Session session = Session.getInstance(props);
        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(protocol(settings));
            store.connect(
                    settings.getImapHost().trim(),
                    settings.getImapUsername() != null ? settings.getImapUsername().trim() : null,
                    settings.getImapPassword());
            String folderName =
                    settings.getImapFolder() != null && !settings.getImapFolder().isBlank()
                            ? settings.getImapFolder().trim()
                            : "INBOX";
            folder = store.getFolder(folderName);
            if (!folder.exists()) {
                throw new MessagingException("Ordner nicht gefunden: " + folderName);
            }
            folder.open(Folder.READ_ONLY);
        } finally {
            closeQuietly(folder, store);
        }
    }

    private static boolean matchesFilters(UnitLeitstellenMailSettings settings, String subject, String from) {
        String fromFilter = trimToNull(settings.getFromFilter());
        if (fromFilter != null) {
            String fromLower = from != null ? from.toLowerCase(Locale.ROOT) : "";
            if (!fromLower.contains(fromFilter.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        String subjectFilter = trimToNull(settings.getSubjectFilter());
        if (subjectFilter != null) {
            String subjectLower = subject != null ? subject.toLowerCase(Locale.ROOT) : "";
            if (!subjectLower.contains(subjectFilter.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private static List<PdfAttachment> extractPdfs(Part part) throws MessagingException {
        List<PdfAttachment> pdfs = new ArrayList<>();
        collectPdfs(part, pdfs);
        return pdfs;
    }

    private static void collectPdfs(Part part, List<PdfAttachment> out) throws MessagingException {
        try {
            if (part.isMimeType("multipart/*")) {
                Multipart multipart = (Multipart) part.getContent();
                for (int i = 0; i < multipart.getCount(); i++) {
                    BodyPart bodyPart = multipart.getBodyPart(i);
                    collectPdfs(bodyPart, out);
                }
                return;
            }
            String disposition = part.getDisposition();
            String filename = decodeFilename(part.getFileName());
            boolean attachmentLike = disposition == null
                    || Part.ATTACHMENT.equalsIgnoreCase(disposition)
                    || Part.INLINE.equalsIgnoreCase(disposition);
            boolean isPdf = (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf"))
                    || part.isMimeType("application/pdf");
            if (attachmentLike && isPdf) {
                byte[] bytes = readBytes(part);
                if (bytes.length > 0) {
                    String name = filename != null && !filename.isBlank() ? filename : "anhang.pdf";
                    out.add(new PdfAttachment(name, bytes));
                }
            }
        } catch (MessagingException e) {
            throw e;
        } catch (Exception e) {
            throw new MessagingException("Anhang konnte nicht gelesen werden: " + e.getMessage(), e);
        }
    }

    private static byte[] readBytes(Part part) throws Exception {
        try (InputStream in = part.getInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    private static String decodeFilename(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MimeUtility.decodeText(raw.trim());
        } catch (Exception e) {
            return raw.trim();
        }
    }

    private static String resolveMessageId(Message message) throws MessagingException {
        String[] ids = message.getHeader("Message-ID");
        if (ids != null && ids.length > 0 && ids[0] != null && !ids[0].isBlank()) {
            return ids[0].trim();
        }
        Instant received = receivedAt(message);
        String subject = safeSubject(message);
        return "generated:" + received.toEpochMilli() + ":" + Integer.toHexString(subject.hashCode());
    }

    private static Instant receivedAt(Message message) throws MessagingException {
        Date received = message.getReceivedDate();
        if (received == null) {
            received = message.getSentDate();
        }
        if (received == null) {
            return Instant.now();
        }
        return received.toInstant();
    }

    private static String safeSubject(Message message) throws MessagingException {
        String subject = message.getSubject();
        if (subject == null) {
            return "";
        }
        try {
            return MimeUtility.decodeText(subject);
        } catch (Exception e) {
            return subject;
        }
    }

    private static String firstFrom(Message message) throws MessagingException {
        Address[] from = message.getFrom();
        if (from == null || from.length == 0) {
            return "";
        }
        if (from[0] instanceof InternetAddress internetAddress) {
            return internetAddress.getAddress() != null ? internetAddress.getAddress() : from[0].toString();
        }
        return from[0].toString();
    }

    private static Properties buildProperties(UnitLeitstellenMailSettings settings) {
        Properties props = new Properties();
        String enc = settings.getImapEncryption() != null ? settings.getImapEncryption() : "SSL";
        int port = settings.getImapPort() != null && settings.getImapPort() > 0
                ? settings.getImapPort()
                : ("SSL".equalsIgnoreCase(enc) ? 993 : 143);
        props.put("mail.store.protocol", protocol(settings));
        props.put("mail.imap.host", settings.getImapHost().trim());
        props.put("mail.imap.port", String.valueOf(port));
        props.put("mail.imaps.host", settings.getImapHost().trim());
        props.put("mail.imaps.port", String.valueOf(port));
        props.put("mail.imap.connectiontimeout", "15000");
        props.put("mail.imap.timeout", "30000");
        props.put("mail.imaps.connectiontimeout", "15000");
        props.put("mail.imaps.timeout", "30000");
        if ("SSL".equalsIgnoreCase(enc)) {
            props.put("mail.imaps.ssl.enable", "true");
            props.put("mail.imap.ssl.enable", "true");
        } else if ("TLS".equalsIgnoreCase(enc)) {
            props.put("mail.imap.starttls.enable", "true");
            props.put("mail.imaps.starttls.enable", "true");
        }
        return props;
    }

    private static String protocol(UnitLeitstellenMailSettings settings) {
        String enc = settings.getImapEncryption() != null ? settings.getImapEncryption() : "SSL";
        return "SSL".equalsIgnoreCase(enc) ? "imaps" : "imap";
    }

    private static void closeQuietly(Folder folder, Store store) {
        if (folder != null) {
            try {
                if (folder.isOpen()) {
                    folder.close(false);
                }
            } catch (Exception ignored) {
            }
        }
        if (store != null) {
            try {
                store.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
