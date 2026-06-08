package de.feuerwehr.manager.atemschutz;

import de.feuerwehr.manager.atemschutz.AtemschutzService.CarrierOverview;
import de.feuerwehr.manager.atemschutz.AtemschutzService.FitnessStatusView;
import de.feuerwehr.manager.mail.UnitMailService;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AtemschutzReminderNotificationService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY);

    private final UnitRepository unitRepository;
    private final ModuleSettingsService moduleSettingsService;
    private final TestModeService testModeService;
    private final AtemschutzService atemschutzService;
    private final AtemschutzSettingsService settingsService;
    private final UnitMailService unitMailService;
    private final UserRepository userRepository;
    private final PersonRepository personRepository;
    private final AtemschutzReminderLogRepository reminderLogRepository;

    @Transactional
    public ReminderRunResult processAllUnits() {
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        for (Unit unit : unitRepository.findActiveVisible(testModeService.isEnabled())) {
            if (!moduleSettingsService.isEnabled(AppModule.ATEMSCHUTZ, unit.getId())) {
                continue;
            }
            if (!unitMailService.canSendForUnit(unit.getId())) {
                continue;
            }
            ReminderRunResult unitResult = processUnit(unit.getId());
            sent += unitResult.sent();
            skipped += unitResult.skipped();
            failed += unitResult.failed();
        }
        return new ReminderRunResult(sent, skipped, failed);
    }

    @Transactional
    public ReminderRunResult processUnit(long unitId) {
        if (!unitMailService.canSendForUnit(unitId)) {
            return new ReminderRunResult(0, 0, 0);
        }
        UnitAtemschutzSettings settings = settingsService.ensureSettings(unitId);
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        List<CarrierOverview> carriers = atemschutzService
                .listCarrierOverviews(unitId, "all")
                .carriers()
                .stream()
                .filter(row -> row.carrier().getStatus() == AtemschutzCarrierStatus.ACTIVE)
                .toList();
        for (CarrierOverview overview : carriers) {
            for (AtemschutzNotificationCategory category : AtemschutzNotificationCategory.values()) {
                boolean notifyCarriers = settingsService.isNotifyCarriers(settings, category);
                List<String> staffEmails = collectStaffEmails(unitId, settings, category);
                if (!notifyCarriers && staffEmails.isEmpty()) {
                    continue;
                }
                FitnessStatusView fitness = overview.summaries().get(category.getFitnessType());
                if (fitness == null || fitness.validUntil() == null) {
                    skipped++;
                    continue;
                }
                AtemschutzReminderMailKind mailKind = switch (fitness.level()) {
                    case WARN -> AtemschutzReminderMailKind.WARNUNG;
                    case OVERDUE -> AtemschutzReminderMailKind.ABGELAUFEN;
                    default -> null;
                };
                if (mailKind == null) {
                    skipped++;
                    continue;
                }
                if (reminderLogRepository.existsByCarrierIdAndFitnessTypeAndMailKindAndValidUntil(
                        overview.carrier().getId(),
                        category.getFitnessType(),
                        mailKind,
                        fitness.validUntil())) {
                    skipped++;
                    continue;
                }
                SendAttempt attempt = sendReminder(
                        unitId,
                        category,
                        mailKind,
                        overview.carrier().getPerson(),
                        fitness.validUntil(),
                        notifyCarriers,
                        staffEmails);
                if (attempt == SendAttempt.SENT) {
                    logReminder(overview, category, mailKind, fitness.validUntil());
                    sent++;
                } else if (attempt == SendAttempt.FAILED) {
                    failed++;
                } else {
                    skipped++;
                }
            }
        }
        return new ReminderRunResult(sent, skipped, failed);
    }

    private void logReminder(
            CarrierOverview overview,
            AtemschutzNotificationCategory category,
            AtemschutzReminderMailKind mailKind,
            LocalDate validUntil) {
        AtemschutzReminderLog logEntry = new AtemschutzReminderLog();
        logEntry.setCarrier(overview.carrier());
        logEntry.setFitnessType(category.getFitnessType());
        logEntry.setMailKind(mailKind);
        logEntry.setValidUntil(validUntil);
        reminderLogRepository.save(logEntry);
    }

    private SendAttempt sendReminder(
            long unitId,
            AtemschutzNotificationCategory category,
            AtemschutzReminderMailKind mailKind,
            Person person,
            LocalDate validUntil,
            boolean notifyCarriers,
            List<String> staffEmails) {
        String templateKey =
                mailKind == AtemschutzReminderMailKind.WARNUNG
                        ? category.getWarnungTemplateKey()
                        : category.getAbgelaufenTemplateKey();
        AtemschutzEmailTemplate template = settingsService
                .findEmailTemplate(unitId, templateKey)
                .orElse(null);
        if (template == null) {
            return SendAttempt.SKIPPED;
        }
        String subject = renderTemplate(template.getSubject(), person, validUntil);
        String body = textToHtml(renderTemplate(template.getBody(), person, validUntil));

        String carrierEmail = notifyCarriers ? resolvePersonEmail(person) : null;
        if (carrierEmail != null) {
            List<String> cc = staffEmails.stream()
                    .filter(email -> !email.equalsIgnoreCase(carrierEmail))
                    .toList();
            return dispatchMail(unitId, carrierEmail, cc, subject, body, person, category);
        }
        if (!staffEmails.isEmpty()) {
            String to = staffEmails.get(0);
            List<String> cc = staffEmails.size() > 1 ? staffEmails.subList(1, staffEmails.size()) : List.of();
            return dispatchMail(unitId, to, cc, subject, body, person, category);
        }
        return SendAttempt.SKIPPED;
    }

    private SendAttempt dispatchMail(
            long unitId,
            String toEmail,
            List<String> ccEmails,
            String subject,
            String body,
            Person person,
            AtemschutzNotificationCategory category) {
        Optional<String> error = unitMailService.sendHtmlMail(unitId, toEmail, ccEmails, subject, body);
        if (error.isPresent()) {
            log.warn(
                    "Atemschutz-Erinnerung fehlgeschlagen (unit={}, person={}, type={}): {}",
                    unitId,
                    person.getId(),
                    category.getFitnessType(),
                    error.get());
            return SendAttempt.FAILED;
        }
        return SendAttempt.SENT;
    }

    private List<String> collectStaffEmails(long unitId, UnitAtemschutzSettings settings, AtemschutzNotificationCategory category) {
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        if (settingsService.isNotifyInstructors(settings, category)) {
            for (Long userId : settingsService.instructorUserIds(settings)) {
                userRepository.findById(userId).map(this::resolveUserEmail).ifPresent(emails::add);
            }
        }
        boolean testData = testModeService.isEnabled();
        for (Long personId : settingsService.ccPersonIds(settings, category)) {
            personRepository
                    .findActiveById(personId, testData)
                    .filter(p -> p.getUnit().getId().equals(unitId))
                    .map(this::resolvePersonEmail)
                    .ifPresent(emails::add);
        }
        return new ArrayList<>(emails);
    }

    private static String renderTemplate(String template, Person person, LocalDate validUntil) {
        if (template == null) {
            return "";
        }
        String firstName = person.getFirstName() != null ? person.getFirstName().trim() : "";
        String lastName = person.getLastName() != null ? person.getLastName().trim() : "";
        String expiry = validUntil != null ? DATE_FMT.format(validUntil) : "";
        return template.replace("{first_name}", firstName)
                .replace("{last_name}", lastName)
                .replace("{expiry_date}", expiry);
    }

    private static String textToHtml(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html><body style=\"font-family:Arial,sans-serif;line-height:1.5;\">"
                + escaped.replace("\n", "<br/>")
                + "</body></html>";
    }

    private String resolvePersonEmail(Person person) {
        if (person == null) {
            return null;
        }
        if (person.getEmail() != null && !person.getEmail().isBlank()) {
            return person.getEmail().trim();
        }
        if (person.getEmailPrivate() != null && !person.getEmailPrivate().isBlank()) {
            return person.getEmailPrivate().trim();
        }
        if (person.getUser() != null) {
            return resolveUserEmail(person.getUser());
        }
        return null;
    }

    private String resolveUserEmail(User user) {
        if (user == null || user.getLoginEmail() == null || user.getLoginEmail().isBlank()) {
            return null;
        }
        return user.getLoginEmail().trim();
    }

    private enum SendAttempt {
        SENT,
        SKIPPED,
        FAILED
    }

    public record ReminderRunResult(int sent, int skipped, int failed) {}
}
