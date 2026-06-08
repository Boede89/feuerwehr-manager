package de.feuerwehr.manager.atemschutz;

import de.feuerwehr.manager.mail.UnitMailService;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StreckePlanungNotificationService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY);

    private final UnitRepository unitRepository;
    private final StreckeTerminRepository terminRepository;
    private final StreckeZuordnungRepository zuordnungRepository;
    private final AtemschutzCarrierRepository carrierRepository;
    private final UserRepository userRepository;
    private final UnitMailService unitMailService;
    private final TestModeService testModeService;
    private final StreckePlanungService streckePlanungService;

    @Transactional(readOnly = true)
    public List<AusbilderOption> listAusbilder(long unitId) {
        return userRepository.findUnitScopedAccountsByUnitId(unitId).stream()
                .filter(u -> u.getLoginEmail() != null && !u.getLoginEmail().isBlank())
                .map(u -> new AusbilderOption(
                        u.getId(),
                        u.getDisplayName() != null && !u.getDisplayName().isBlank()
                                ? u.getDisplayName()
                                : u.getUsername(),
                        u.getLoginEmail()))
                .sorted(Comparator.comparing(AusbilderOption::name))
                .toList();
    }

    @Transactional
    public NotifyResult notifyCarrier(long unitId, long terminId, long carrierId) {
        StreckeTermin termin = requireTermin(unitId, terminId);
        AtemschutzCarrier carrier = requireCarrier(unitId, carrierId);
        String email = resolvePersonEmail(carrier.getPerson());
        if (email == null) {
            return NotifyResult.failure("Keine E-Mail-Adresse hinterlegt.");
        }
        String subject = "Übungsstrecke – Ihr Termin am " + DATE_FMT.format(termin.getTerminDatum());
        Optional<String> error = unitMailService.sendHtmlMail(unitId, email, subject, buildParticipantMail(carrier, termin));
        if (error.isPresent()) {
            return NotifyResult.failure(error.get());
        }
        markNotified(terminId, carrierId);
        return NotifyResult.success("E-Mail an " + email + " gesendet.");
    }

    @Transactional
    public NotifyResult notifyTermin(long unitId, long terminId) {
        StreckeTermin termin = requireTermin(unitId, terminId);
        List<StreckeZuordnung> zuordnungen = zuordnungRepository.findByTerminIdsOrEmpty(List.of(terminId));
        if (zuordnungen.isEmpty()) {
            return NotifyResult.failure("Keine Geräteträger zugeordnet.");
        }
        int sent = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (StreckeZuordnung zuordnung : zuordnungen) {
            AtemschutzCarrier carrier = zuordnung.getCarrier();
            String email = resolvePersonEmail(carrier.getPerson());
            if (email == null) {
                failed++;
                continue;
            }
            String subject = "Übungsstrecke – Ihr Termin am " + DATE_FMT.format(termin.getTerminDatum());
            Optional<String> error =
                    unitMailService.sendHtmlMail(unitId, email, subject, buildParticipantMail(carrier, termin));
            if (error.isPresent()) {
                failed++;
                errors.add(error.get());
            } else {
                sent++;
                markNotified(terminId, carrier.getId());
            }
        }
        if (sent == 0) {
            String msg = failed > 0 ? "Keine E-Mail konnte gesendet werden." : "Keine E-Mail-Adressen hinterlegt.";
            if (!errors.isEmpty()) {
                msg += " " + errors.get(0);
            }
            return NotifyResult.failure(msg);
        }
        String message = sent + " E-Mail(s) gesendet";
        if (failed > 0) {
            message += ", " + failed + " fehlgeschlagen";
        }
        return NotifyResult.success(message);
    }

    @Transactional
    public NotifyResult notifyAll(long unitId) {
        boolean testData = testModeService.isEnabled();
        LocalDate today = LocalDate.now();
        List<StreckeTermin> termine = terminRepository.findRecentByUnit(unitId, today, testData).stream()
                .filter(t -> !t.getTerminDatum().isBefore(today))
                .toList();
        if (termine.isEmpty()) {
            return NotifyResult.failure("Keine zukünftigen Termine vorhanden.");
        }
        Map<Long, StreckeTermin> terminById = termine.stream()
                .collect(java.util.stream.Collectors.toMap(StreckeTermin::getId, t -> t, (a, b) -> a));
        List<StreckeZuordnung> zuordnungen =
                zuordnungRepository.findByTerminIdsOrEmpty(termine.stream().map(StreckeTermin::getId).toList());
        if (zuordnungen.isEmpty()) {
            return NotifyResult.failure("Keine Zuordnungen mit E-Mail-Adressen gefunden.");
        }
        int sent = 0;
        int failed = 0;
        for (StreckeZuordnung zuordnung : zuordnungen) {
            StreckeTermin termin = terminById.get(zuordnung.getTermin().getId());
            if (termin == null) {
                continue;
            }
            AtemschutzCarrier carrier = zuordnung.getCarrier();
            String email = resolvePersonEmail(carrier.getPerson());
            if (email == null) {
                failed++;
                continue;
            }
            String subject = "Übungsstrecke – Ihr Termin am " + DATE_FMT.format(termin.getTerminDatum());
            Optional<String> error =
                    unitMailService.sendHtmlMail(unitId, email, subject, buildParticipantMail(carrier, termin));
            if (error.isPresent()) {
                failed++;
            } else {
                sent++;
                markNotified(termin.getId(), carrier.getId());
            }
        }
        if (sent == 0) {
            return NotifyResult.failure("Keine E-Mails gesendet. Prüfen Sie E-Mail-Adressen und SMTP-Einstellungen.");
        }
        String message = sent + " E-Mail(s) gesendet";
        if (failed > 0) {
            message += ", " + failed + " fehlgeschlagen";
        }
        return NotifyResult.success(message);
    }

    @Transactional
    public NotifyResult notifyAusbilder(long unitId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return NotifyResult.failure("Keine Ausbilder ausgewählt.");
        }
        Unit unit = unitRepository
                .findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        StreckePlanungService.StreckePlanungView view = streckePlanungService.loadView(unitId, false);
        String subject = "Übungsstrecke – Planungsübersicht";
        String body = buildAusbilderOverviewMail(unit.getName(), view);
        int sent = 0;
        int failed = 0;
        for (Long userId : userIds) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getLoginEmail() == null || user.getLoginEmail().isBlank()) {
                failed++;
                continue;
            }
            if (user.getUnit() != null && !user.getUnit().getId().equals(unitId)
                    && !user.getRole().isAdminLevel()) {
                failed++;
                continue;
            }
            Optional<String> error = unitMailService.sendHtmlMail(unitId, user.getLoginEmail(), subject, body);
            if (error.isPresent()) {
                failed++;
            } else {
                sent++;
            }
        }
        if (sent == 0) {
            return NotifyResult.failure("Keine E-Mail konnte gesendet werden.");
        }
        String message = sent + " E-Mail(s) an Ausbilder gesendet";
        if (failed > 0) {
            message += ", " + failed + " fehlgeschlagen";
        }
        return NotifyResult.success(message);
    }

    private void markNotified(long terminId, long carrierId) {
        zuordnungRepository
                .findByTerminIdAndCarrierId(terminId, carrierId)
                .ifPresent(z -> z.setBenachrichtigtAm(Instant.now()));
    }

    private StreckeTermin requireTermin(long unitId, long terminId) {
        return terminRepository
                .findByIdAndUnit(terminId, unitId, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Termin nicht gefunden."));
    }

    private AtemschutzCarrier requireCarrier(long unitId, long carrierId) {
        AtemschutzCarrier carrier = carrierRepository
                .findByIdAndTestData(carrierId, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Geräteträger nicht gefunden."));
        if (carrier.getUnit().getId() != unitId) {
            throw new IllegalArgumentException("Geräteträger gehört nicht zu dieser Einheit.");
        }
        return carrier;
    }

    private static String resolvePersonEmail(Person person) {
        if (person == null) {
            return null;
        }
        if (person.getEmail() != null && !person.getEmail().isBlank()) {
            return person.getEmail().trim();
        }
        if (person.getEmailPrivate() != null && !person.getEmailPrivate().isBlank()) {
            return person.getEmailPrivate().trim();
        }
        if (person.getUser() != null
                && person.getUser().getLoginEmail() != null
                && !person.getUser().getLoginEmail().isBlank()) {
            return person.getUser().getLoginEmail().trim();
        }
        return null;
    }

    private static String buildParticipantMail(AtemschutzCarrier carrier, StreckeTermin termin) {
        String name = carrier.getPerson().displayName();
        String datum = DATE_FMT.format(termin.getTerminDatum());
        String zeit = TIME_FMT.format(termin.getTerminZeit());
        String ort = termin.getOrt() != null && !termin.getOrt().isBlank()
                ? escape(termin.getOrt())
                : "wird noch bekannt gegeben";
        return """
                <!DOCTYPE html><html lang="de"><head><meta charset="UTF-8"></head><body style="font-family:Arial,sans-serif;line-height:1.6;color:#333;">
                <div style="max-width:600px;margin:0 auto;background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 2px 10px rgba(0,0,0,0.1);">
                <div style="background:linear-gradient(135deg,#667eea 0%%,#764ba2 100%%);color:#fff;padding:30px;text-align:center;">
                <h1 style="margin:0;font-size:24px;">Übungsstrecke – Termineinladung</h1></div>
                <div style="padding:30px;">
                <p>Hallo %s,</p>
                <p>Sie wurden für folgenden <strong>Übungsstrecken-Termin</strong> eingeplant:</p>
                <div style="background:#f8f9fa;border-left:4px solid #667eea;padding:20px;margin:20px 0;border-radius:0 8px 8px 0;">
                <p style="margin:8px 0;"><strong>Datum:</strong> %s</p>
                <p style="margin:8px 0;"><strong>Uhrzeit:</strong> %s Uhr</p>
                <p style="margin:8px 0;"><strong>Ort:</strong> %s</p>
                </div>
                <div style="background:#fff3cd;border:1px solid #ffc107;padding:15px;border-radius:8px;margin:20px 0;">
                <strong>Wichtig:</strong> Bitte bringen Sie Ihre Ausrüstung und einen gültigen Ausweis mit.
                Falls Sie den Termin nicht wahrnehmen können, melden Sie sich bitte rechtzeitig ab.
                </div>
                <p>Mit freundlichen Grüßen,<br><strong>Ihre Feuerwehr</strong></p>
                </div></div></body></html>
                """
                .formatted(escape(name), datum, zeit, ort);
    }

    private static String buildAusbilderOverviewMail(
            String unitName, StreckePlanungService.StreckePlanungView view) {
        StringBuilder rows = new StringBuilder();
        LocalDate today = LocalDate.now();
        for (StreckePlanungService.TerminView termin : view.termine()) {
            if (termin.datum().isBefore(today)) {
                continue;
            }
            String teilnehmer = termin.teilnehmer().isEmpty()
                    ? "<em>keine Zuordnungen</em>"
                    : escape(termin.teilnehmer().stream()
                            .map(StreckePlanungService.CarrierAssignmentView::name)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse(""));
            rows.append("<tr><td>")
                    .append(DATE_FMT.format(termin.datum()))
                    .append("</td><td>")
                    .append(TIME_FMT.format(termin.zeit()))
                    .append(" Uhr</td><td>")
                    .append(escape(blankToDash(termin.ort())))
                    .append("</td><td>")
                    .append(termin.aktuelleTeilnehmer())
                    .append("/")
                    .append(termin.maxTeilnehmer())
                    .append("</td><td>")
                    .append(teilnehmer)
                    .append("</td></tr>");
        }
        String pool = view.unassignedCarriers().isEmpty()
                ? "<p><em>Alle Geräteträger sind zugeordnet.</em></p>"
                : "<ul>" + view.unassignedCarriers().stream()
                        .map(c -> "<li>" + escape(c.name()) + "</li>")
                        .reduce((a, b) -> a + b)
                        .orElse("") + "</ul>";
        return """
                <!DOCTYPE html><html lang="de"><head><meta charset="UTF-8"></head>
                <body style="font-family:Arial,sans-serif;line-height:1.6;color:#333;">
                <div style="max-width:800px;margin:0 auto;background:#fff;border-radius:10px;overflow:hidden;">
                <div style="background:linear-gradient(135deg,#667eea 0%%,#764ba2 100%%);color:#fff;padding:24px;text-align:center;">
                <h1 style="margin:0;">Übungsstrecke – Planungsübersicht</h1>
                <p style="margin:8px 0 0;">%s</p></div>
                <div style="padding:24px;">
                <p><strong>Zusammenfassung:</strong> %d Termine, %d Geräteträger noch nicht zugeordnet</p>
                <h2>Geplante Termine</h2>
                <table style="width:100%%;border-collapse:collapse;" cellpadding="8">
                <thead><tr style="background:#667eea;color:#fff;">
                <th>Datum</th><th>Uhrzeit</th><th>Ort</th><th>Belegung</th><th>Teilnehmer</th></tr></thead>
                <tbody>%s</tbody></table>
                <h2>Nicht zugeordnete Geräteträger</h2>
                %s
                </div></div></body></html>
                """
                .formatted(
                        escape(unitName),
                        view.termine().size(),
                        view.unassignedCarriers().size(),
                        rows,
                        pool);
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public record AusbilderOption(long id, String name, String email) {}

    public record NotifyResult(boolean ok, String message) {
        static NotifyResult success(String message) {
            return new NotifyResult(true, message);
        }

        static NotifyResult failure(String message) {
            return new NotifyResult(false, message);
        }
    }
}
