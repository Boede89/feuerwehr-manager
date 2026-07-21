package de.feuerwehr.manager.reservierungen;

import de.feuerwehr.manager.mail.UnitMailService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
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

    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY).withZone(ZoneId.of("Europe/Berlin"));

    private final UnitMailService unitMailService;
    private final UserRepository userRepository;
    private final ReservierungenSettingsService settingsService;

    public void notifyAdminsNewVehicleReservation(long unitId, VehicleReservation reservation) {
        UnitReservierungenSettings settings = settingsService.ensureSettings(unitId);
        notifyAdmins(
                unitId,
                settingsService.vehicleNotificationUserIds(settings),
                "Neue Fahrzeugreservierung – " + reservation.getVehicle().getName(),
                buildNewRequestHtml(
                        "Fahrzeug",
                        reservation.getVehicle().getName(),
                        reservation.getRequesterName(),
                        reservation.getRequesterEmail(),
                        reservation.getReason(),
                        reservation.getLocation(),
                        reservation.getStartAt(),
                        reservation.getEndAt()));
    }

    public void notifyAdminsNewRoomReservation(long unitId, RoomReservation reservation) {
        UnitReservierungenSettings settings = settingsService.ensureSettings(unitId);
        notifyAdmins(
                unitId,
                settingsService.roomNotificationUserIds(settings),
                "Neue Raumreservierung – " + reservation.getRoom().getName(),
                buildNewRequestHtml(
                        "Raum",
                        reservation.getRoom().getName(),
                        reservation.getRequesterName(),
                        reservation.getRequesterEmail(),
                        reservation.getReason(),
                        reservation.getLocation(),
                        reservation.getStartAt(),
                        reservation.getEndAt()));
    }

    public void notifyRequesterApproved(String email, String resourceLabel, String resourceName) {
        sendSimple(email, "Reservierung genehmigt – " + resourceName, """
                <p>Ihre Reservierung für <strong>%s %s</strong> wurde genehmigt.</p>
                """.formatted(escape(resourceLabel), escape(resourceName)));
    }

    public void notifyRequesterRejected(String email, String resourceLabel, String resourceName, String reason) {
        sendSimple(email, "Reservierung abgelehnt – " + resourceName, """
                <p>Ihre Reservierung für <strong>%s %s</strong> wurde abgelehnt.</p>
                <p><strong>Begründung:</strong> %s</p>
                """.formatted(escape(resourceLabel), escape(resourceName), escape(reason != null ? reason : "—")));
    }

    public void notifyRequesterCancelled(String email, String resourceLabel, String resourceName) {
        sendSimple(email, "Reservierung storniert – " + resourceName, """
                <p>Ihre genehmigte Reservierung für <strong>%s %s</strong> wurde wegen eines Konflikts storniert.</p>
                """.formatted(escape(resourceLabel), escape(resourceName)));
    }

    private void notifyAdmins(long unitId, List<Long> userIds, String subject, String htmlBody) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        if (!unitMailService.canSendForUnit(unitId)) {
            log.debug("SMTP nicht konfiguriert – Reservierungsbenachrichtigung übersprungen (Einheit {}).", unitId);
            return;
        }
        Set<String> sent = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId == null || userId <= 0) {
                continue;
            }
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || !user.isActive()) {
                continue;
            }
            String email = resolveEmail(user);
            if (email == null || email.isBlank() || !sent.add(email.toLowerCase(Locale.ROOT))) {
                continue;
            }
            unitMailService.sendHtmlMail(unitId, email, subject, wrapHtml(subject, htmlBody));
        }
    }

    private void sendSimple(String email, String subject, String bodyHtml) {
        if (email == null || email.isBlank()) {
            return;
        }
        // Ohne unitId kein Versand – wird vom Aufrufer mit unitId versendet
    }

    public void notifyRequesterApproved(long unitId, String email, String resourceLabel, String resourceName) {
        if (!unitMailService.canSendForUnit(unitId) || email == null || email.isBlank()) {
            return;
        }
        unitMailService.sendHtmlMail(
                unitId,
                email,
                "Reservierung genehmigt – " + resourceName,
                wrapHtml(
                        "Reservierung genehmigt",
                        """
                        <p>Ihre Reservierung für <strong>%s %s</strong> wurde genehmigt.</p>
                        """
                                .formatted(escape(resourceLabel), escape(resourceName))));
    }

    public void notifyRequesterRejected(long unitId, String email, String resourceLabel, String resourceName, String reason) {
        if (!unitMailService.canSendForUnit(unitId) || email == null || email.isBlank()) {
            return;
        }
        unitMailService.sendHtmlMail(
                unitId,
                email,
                "Reservierung abgelehnt – " + resourceName,
                wrapHtml(
                        "Reservierung abgelehnt",
                        """
                        <p>Ihre Reservierung für <strong>%s %s</strong> wurde abgelehnt.</p>
                        <p><strong>Begründung:</strong> %s</p>
                        """
                                .formatted(escape(resourceLabel), escape(resourceName), escape(reason != null ? reason : "—"))));
    }

    public void notifyRequesterCancelled(long unitId, String email, String resourceLabel, String resourceName) {
        if (!unitMailService.canSendForUnit(unitId) || email == null || email.isBlank()) {
            return;
        }
        unitMailService.sendHtmlMail(
                unitId,
                email,
                "Reservierung storniert – " + resourceName,
                wrapHtml(
                        "Reservierung storniert",
                        """
                        <p>Ihre genehmigte Reservierung für <strong>%s %s</strong> wurde wegen eines Konflikts storniert.</p>
                        """
                                .formatted(escape(resourceLabel), escape(resourceName))));
    }

    private String buildNewRequestHtml(
            String typeLabel,
            String resourceName,
            String requesterName,
            String requesterEmail,
            String reason,
            String location,
            java.time.Instant startAt,
            java.time.Instant endAt) {
        return """
                <p>Ein neuer Antrag für eine %sreservierung ist eingegangen.</p>
                <table style="width:100%%;border-collapse:collapse;">
                  <tr><td style="padding:6px 0;font-weight:600;">%s</td><td>%s</td></tr>
                  <tr><td style="padding:6px 0;font-weight:600;">Antragsteller</td><td>%s</td></tr>
                  <tr><td style="padding:6px 0;font-weight:600;">E-Mail</td><td>%s</td></tr>
                  <tr><td style="padding:6px 0;font-weight:600;">Grund</td><td>%s</td></tr>
                  <tr><td style="padding:6px 0;font-weight:600;">Ort</td><td>%s</td></tr>
                  <tr><td style="padding:6px 0;font-weight:600;">Zeitraum</td><td>%s – %s</td></tr>
                </table>
                """
                .formatted(
                        escape(typeLabel.toLowerCase(Locale.ROOT)),
                        escape(typeLabel),
                        escape(resourceName),
                        escape(requesterName),
                        escape(requesterEmail),
                        escape(reason),
                        escape(location != null && !location.isBlank() ? location : "—"),
                        DISPLAY.format(startAt),
                        DISPLAY.format(endAt));
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
