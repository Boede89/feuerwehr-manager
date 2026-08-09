package de.feuerwehr.manager.reservierungen;

import de.feuerwehr.manager.berichte.TestModeEmailContext;
import de.feuerwehr.manager.berichte.TestModeEmailDelivery;
import de.feuerwehr.manager.mail.UnitMailService;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservierungenNotificationService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY).withZone(ZONE);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY).withZone(ZONE);

    private final UnitMailService unitMailService;
    private final UserRepository userRepository;
    private final ReservierungenSettingsService settingsService;
    private final GlobalSettingsService globalSettingsService;
    private final TestModeService testModeService;

    public void notifyAdminsNewVehicleReservation(long unitId, VehicleReservation reservation) {
        notifyAdminsNewVehicleReservation(unitId, reservation, 1);
    }

    public void notifyAdminsNewVehicleReservation(long unitId, VehicleReservation reservation, int totalCreated) {
        UnitReservierungenSettings settings = settingsService.ensureSettings(unitId);
        String names = reservation.vehicleNamesJoined();
        String subject = totalCreated > 1
                ? "Neue Fahrzeugreservierungen (" + totalCreated + ") – " + names
                : "Neue Fahrzeugreservierung – " + names;
        notifyAdmins(
                unitId,
                settingsService.vehicleNotificationRecipients(settings),
                subject,
                buildNewRequestHtml(
                        unitId,
                        "Fahrzeug",
                        names,
                        reservation.getRequesterName(),
                        reservation.getRequesterEmail(),
                        reservation.getReason(),
                        reservation.getLocation(),
                        reservation.getStartAt(),
                        reservation.getEndAt(),
                        totalCreated));
    }

    public void notifyAdminsNewRoomReservation(long unitId, RoomReservation reservation) {
        notifyAdminsNewRoomReservation(unitId, reservation, 1);
    }

    public void notifyAdminsNewRoomReservation(long unitId, RoomReservation reservation, int totalCreated) {
        UnitReservierungenSettings settings = settingsService.ensureSettings(unitId);
        String subject = totalCreated > 1
                ? "Neue Raumreservierungen (" + totalCreated + ") – " + reservation.getRoom().getName()
                : "Neue Raumreservierung – " + reservation.getRoom().getName();
        notifyAdmins(
                unitId,
                settingsService.roomNotificationRecipients(settings),
                subject,
                buildNewRequestHtml(
                        unitId,
                        "Raum",
                        reservation.getRoom().getName(),
                        reservation.getRequesterName(),
                        reservation.getRequesterEmail(),
                        reservation.getReason(),
                        reservation.getLocation(),
                        reservation.getStartAt(),
                        reservation.getEndAt(),
                        totalCreated));
    }

    public void notifyRequesterDecision(
            long unitId, VehicleReservation reservation, boolean approved, String rejectionReason) {
        sendDecisionMail(
                unitId,
                reservation.getRequesterEmail(),
                approved,
                "Fahrzeug",
                reservation.vehicleNamesJoined(),
                reservation.getReason(),
                reservation.getLocation(),
                reservation.getStartAt(),
                reservation.getEndAt(),
                rejectionReason);
    }

    public void notifyRequesterDecision(
            long unitId, RoomReservation reservation, boolean approved, String rejectionReason) {
        sendDecisionMail(
                unitId,
                reservation.getRequesterEmail(),
                approved,
                "Raum",
                reservation.getRoom().getName(),
                reservation.getReason(),
                reservation.getLocation(),
                reservation.getStartAt(),
                reservation.getEndAt(),
                rejectionReason);
    }

    public void notifyRequesterCancelled(
            long unitId,
            String email,
            String resourceLabel,
            String resourceName,
            String reason,
            String location,
            Instant startAt,
            Instant endAt,
            String introMessage,
            String cancellationReason) {
        if (!isFutureReservation(startAt)) {
            return;
        }
        if (!unitMailService.canSendForUnit(unitId) || email == null || email.isBlank()) {
            return;
        }
        List<String> recipients = resolveMailRecipients(unitId, List.of(email.trim()));
        if (recipients.isEmpty()) {
            return;
        }
        String subject = "Reservierung storniert – " + resourceName;
        String cancellationBlock =
                cancellationReason != null && !cancellationReason.isBlank()
                        ? "<tr><td style=\"padding:6px 0;font-weight:600;\">Stornierungsgrund</td><td>"
                                + escape(cancellationReason.trim())
                                + "</td></tr>"
                        : "";
        String body = """
                <p style="color:#b91c1c;font-weight:700;">%s</p>
                <table style="width:100%%;border-collapse:collapse;margin-top:12px;">
                  <tr><td style="padding:6px 0;font-weight:600;">%s</td><td>%s</td></tr>
                  <tr><td style="padding:6px 0;font-weight:600;">Grund</td><td>%s</td></tr>
                  <tr><td style="padding:6px 0;font-weight:600;">Ort</td><td>%s</td></tr>
                  <tr><td style="padding:6px 0;font-weight:600;">Zeitraum</td><td>%s</td></tr>
                  %s
                </table>
                """
                .formatted(
                        escape(introMessage != null ? introMessage : "Ihre Reservierung wurde storniert."),
                        escape(resourceLabel),
                        escape(resourceName),
                        escape(blankToDash(reason)),
                        escape(blankToDash(location)),
                        escape(formatTimeRange(startAt, endAt)),
                        cancellationBlock);
        for (String recipient : recipients) {
            unitMailService.sendHtmlMail(unitId, recipient, subject, wrapHtml(subject, body));
        }
    }

    private void sendDecisionMail(
            long unitId,
            String email,
            boolean approved,
            String typeLabel,
            String resourceName,
            String reason,
            String location,
            Instant startAt,
            Instant endAt,
            String rejectionReason) {
        if (!unitMailService.canSendForUnit(unitId) || email == null || email.isBlank()) {
            return;
        }
        List<String> recipients = resolveMailRecipients(unitId, List.of(email.trim()));
        if (recipients.isEmpty()) {
            return;
        }
        String subject = (approved ? "Reservierung genehmigt" : "Reservierung abgelehnt") + " – " + resourceName;
        String statusLine = approved
                ? "<p style=\"color:#15803d;font-weight:700;\">Ihre Reservierung wurde genehmigt.</p>"
                : "<p style=\"color:#b91c1c;font-weight:700;\">Ihre Reservierung wurde abgelehnt.</p>";
        String reasonBlock = approved
                ? ""
                : "<tr><td style=\"padding:6px 0;font-weight:600;\">Ablehnungsgrund</td><td>"
                        + escape(rejectionReason != null && !rejectionReason.isBlank() ? rejectionReason : "—")
                        + "</td></tr>";
        String body = statusLine
                + """
                <table style="width:100%%;border-collapse:collapse;margin-top:12px;">
                  <tr><td style="padding:6px 0;font-weight:600;">%s</td><td>%s</td></tr>
                  <tr><td style="padding:6px 0;font-weight:600;">Grund</td><td>%s</td></tr>
                  <tr><td style="padding:6px 0;font-weight:600;">Ort</td><td>%s</td></tr>
                  <tr><td style="padding:6px 0;font-weight:600;">Zeitraum</td><td>%s</td></tr>
                  %s
                </table>
                """
                        .formatted(
                                escape(typeLabel),
                                escape(resourceName),
                                escape(reason),
                                escape(blankToDash(location)),
                                escape(formatTimeRange(startAt, endAt)),
                                reasonBlock);
        for (String recipient : recipients) {
            unitMailService.sendHtmlMail(unitId, recipient, subject, wrapHtml(subject, body));
        }
    }

    private void notifyAdmins(
            long unitId,
            ReservierungenSettingsService.NotificationRecipients recipients,
            String subject,
            String htmlBody) {
        if (!unitMailService.canSendForUnit(unitId)) {
            log.debug("SMTP nicht konfiguriert – Reservierungsbenachrichtigung übersprungen (Einheit {}).", unitId);
            return;
        }
        Set<String> configured = new LinkedHashSet<>();
        if (recipients != null) {
            List<Long> userIds = recipients.userIds() != null ? recipients.userIds() : List.of();
            for (Long userId : userIds) {
                if (userId == null || userId <= 0) {
                    continue;
                }
                User user = userRepository.findById(userId).orElse(null);
                if (user == null || !user.isActive()) {
                    continue;
                }
                String email = resolveEmail(user);
                if (email == null || email.isBlank()) {
                    continue;
                }
                configured.add(email.trim());
            }
            List<String> extraEmails = recipients.emails() != null ? recipients.emails() : List.of();
            for (String email : extraEmails) {
                if (email == null || email.isBlank()) {
                    continue;
                }
                configured.add(email.trim());
            }
        }
        List<String> targets = resolveMailRecipients(unitId, List.copyOf(configured));
        for (String email : targets) {
            unitMailService.sendHtmlMail(unitId, email, subject, wrapHtml(subject, htmlBody));
        }
    }

    /**
     * Im Testmodus: NONE = keine Mail, SELF = nur aktuelle Login-E-Mail,
     * CONFIGURED = hinterlegte Empfänger. Außerhalb Testmodus unverändert.
     */
    private List<String> resolveMailRecipients(long unitId, List<String> configuredRecipients) {
        if (!testModeService.isEnabled()) {
            return configuredRecipients == null ? List.of() : configuredRecipients;
        }
        TestModeEmailDelivery delivery = TestModeEmailContext.getDelivery();
        if (delivery == TestModeEmailDelivery.NONE) {
            log.info(
                    "Testmodus: Reservierungs-E-Mail nicht gesendet (Auswahl: keine E-Mail, Einheit {}).",
                    unitId);
            return List.of();
        }
        if (delivery == TestModeEmailDelivery.SELF) {
            String actorEmail = TestModeEmailContext.getActorEmail();
            if (actorEmail == null || actorEmail.isBlank()) {
                log.warn(
                        "Testmodus: Reservierungs-E-Mail nicht gesendet — keine Login-E-Mail (Einheit {}).",
                        unitId);
                return List.of();
            }
            return List.of(actorEmail.trim());
        }
        if (configuredRecipients == null || configuredRecipients.isEmpty()) {
            log.info(
                    "Testmodus: Reservierungs-E-Mail nicht gesendet — keine hinterlegten Empfänger (Einheit {}).",
                    unitId);
            return List.of();
        }
        return configuredRecipients;
    }

    private String buildNewRequestHtml(
            long unitId,
            String typeLabel,
            String resourceName,
            String requesterName,
            String requesterEmail,
            String reason,
            String location,
            Instant startAt,
            Instant endAt,
            int totalCreated) {
        String manageUrl = buildManageUrl(unitId);
        String cta = manageUrl == null
                ? "<p>Bitte im Feuerwehr-Manager unter <strong>Reservierungen → Verwaltung</strong> bearbeiten.</p>"
                : """
                  <p style="margin:18px 0 8px;">
                    <a href="%s" style="background:#e63022;color:#fff;padding:10px 16px;text-decoration:none;border-radius:6px;display:inline-block;font-weight:600;">
                      Zur Genehmigung öffnen
                    </a>
                  </p>
                  <p style="color:#64748b;font-size:13px;">Oder im System: Reservierungen → Verwaltung</p>
                  """
                        .formatted(escape(manageUrl));
        String multiNote = totalCreated > 1
                ? "<p>Es wurden <strong>" + totalCreated + "</strong> Termine in diesem Antrag eingereicht.</p>"
                : "";
        return """
                <p>Ein neuer Antrag für eine %sreservierung ist eingegangen.</p>
                %s
                <table style="width:100%%;border-collapse:collapse;">
                  <tr><td style="padding:6px 0;font-weight:600;">%s</td><td>%s</td></tr>
                  <tr><td style="padding:6px 0;font-weight:600;">Antragsteller</td><td>%s</td></tr>
                  <tr><td style="padding:6px 0;font-weight:600;">E-Mail</td><td>%s</td></tr>
                  <tr><td style="padding:6px 0;font-weight:600;">Grund</td><td>%s</td></tr>
                  <tr><td style="padding:6px 0;font-weight:600;">Ort</td><td>%s</td></tr>
                  <tr><td style="padding:6px 0;font-weight:600;">Zeitraum (Beispiel)</td><td>%s</td></tr>
                </table>
                %s
                """
                .formatted(
                        escape(typeLabel.toLowerCase(Locale.ROOT)),
                        multiNote,
                        escape(typeLabel),
                        escape(resourceName),
                        escape(requesterName),
                        escape(requesterEmail),
                        escape(reason),
                        escape(blankToDash(location)),
                        escape(formatTimeRange(startAt, endAt)),
                        cta);
    }

    private String buildManageUrl(long unitId) {
        String base = globalSettingsService.get().getAppUrl();
        if (base == null || base.isBlank()) {
            return null;
        }
        String normalized = base.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/reservierungen?unit=" + unitId + "&tab=verwaltung";
    }

    private static String wrapHtml(String title, String body) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:640px;margin:0 auto;">
                  <div style="background:#e63022;color:#fff;padding:16px 20px;border-radius:8px 8px 0 0;">
                    <h2 style="margin:0;font-size:18px;">%s</h2>
                  </div>
                  <div style="background:#fff;border:1px solid #e2e8f0;border-top:none;padding:20px;border-radius:0 0 8px 8px;">
                    %s
                  </div>
                </div>
                """
                .formatted(escape(title), body);
    }

    private static String resolveEmail(User user) {
        if (user.getLoginEmail() != null && !user.getLoginEmail().isBlank()) {
            return user.getLoginEmail().trim();
        }
        return null;
    }

    private static String formatTimeRange(Instant startAt, Instant endAt) {
        if (startAt == null && endAt == null) {
            return "—";
        }
        if (startAt == null) {
            return TIME.format(endAt) + " Uhr";
        }
        if (endAt == null) {
            return DATE.format(startAt) + " " + TIME.format(startAt) + " Uhr";
        }
        String startDate = DATE.format(startAt);
        String endDate = DATE.format(endAt);
        String startTime = TIME.format(startAt) + " Uhr";
        String endTime = TIME.format(endAt) + " Uhr";
        if (startDate.equals(endDate)) {
            return startDate + " " + startTime + " – " + endTime;
        }
        return startDate + " " + startTime + " – " + endDate + " " + endTime;
    }

    private static boolean isFutureReservation(Instant startAt) {
        return startAt != null && startAt.isAfter(Instant.now());
    }

    private static String blankToDash(String value) {
        return value != null && !value.isBlank() ? value : "—";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
