package de.feuerwehr.manager.atemschutz;

import de.feuerwehr.manager.atemschutz.AtemschutzService.CarrierDetailView;
import de.feuerwehr.manager.atemschutz.AtemschutzService.CarrierOverview;
import de.feuerwehr.manager.atemschutz.AtemschutzService.FitnessStatusView;
import de.feuerwehr.manager.berichte.TestModeEmailContext;
import de.feuerwehr.manager.berichte.TestModeEmailDelivery;
import de.feuerwehr.manager.mail.UnitMailService;
import de.feuerwehr.manager.notification.UserNotificationPreferenceService;
import de.feuerwehr.manager.notification.UserNotificationTopic;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import de.feuerwehr.manager.util.PersonMembership;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
    private final UserNotificationPreferenceService userNotificationPreferenceService;

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
                .filter(row -> PersonMembership.isCurrentlyMember(row.carrier().getPerson()))
                .toList();
        for (CarrierOverview overview : carriers) {
            List<ReminderItem> carrierItems = new ArrayList<>();
            List<ReminderItem> staffItems = new ArrayList<>();
            for (ReminderItem item : collectEligibleItems(overview.summaries())) {
                boolean notifyCarrier = settingsService.isNotifyCarriers(settings, item.category());
                List<String> staffEmails = collectStaffEmails(unitId, settings, item.category());
                Optional<AtemschutzReminderLog> existing = reminderLogRepository
                        .findByCarrierIdAndFitnessTypeAndMailKindAndValidUntil(
                                overview.carrier().getId(),
                                item.category().getFitnessType(),
                                item.mailKind(),
                                item.validUntil());
                if (notifyCarrier) {
                    if (existing.isPresent() && existing.get().isCarrierNotified()) {
                        skipped++;
                        continue;
                    }
                    carrierItems.add(item);
                } else if (!staffEmails.isEmpty()) {
                    if (existing.isPresent()) {
                        skipped++;
                        continue;
                    }
                    staffItems.add(item);
                } else {
                    skipped++;
                }
            }
            if (!carrierItems.isEmpty()) {
                List<String> ccEmails = unionStaffEmails(unitId, settings, carrierItems);
                SendAttempt attempt = sendCarrierDigest(unitId, overview.carrier(), carrierItems, ccEmails);
                if (attempt == SendAttempt.SENT) {
                    boolean carrierNotified = countAsCarrierNotified(overview.carrier().getPerson());
                    logItems(overview.carrier(), carrierItems, carrierNotified);
                    sent++;
                } else if (attempt == SendAttempt.FAILED) {
                    failed++;
                } else {
                    skipped++;
                }
            }
            for (ReminderItem item : staffItems) {
                List<String> staffEmails = collectStaffEmails(unitId, settings, item.category());
                SendAttempt attempt = sendReminder(
                        unitId,
                        item.category(),
                        item.mailKind(),
                        overview.carrier().getPerson(),
                        item.validUntil(),
                        false,
                        staffEmails);
                if (attempt == SendAttempt.SENT) {
                    logReminder(overview.carrier(), item, false);
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

    @Transactional
    public ManualReminderResult sendManualForCarrier(long unitId, long carrierId) {
        requireMailReady(unitId);
        AtemschutzCarrier carrier = atemschutzService.requireCarrier(carrierId);
        requireCarrierInUnit(carrier, unitId);
        if (!PersonMembership.isCurrentlyMember(carrier.getPerson())) {
            throw new IllegalArgumentException("Ausgetretene Geräteträger können nicht benachrichtigt werden.");
        }
        CarrierDetailView detail = atemschutzService.loadCarrierDetail(carrierId);
        List<ReminderItem> items = collectEligibleItems(detail.summaries());
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Aktuell liegt keine Warnung und kein Ablauf vor.");
        }
        requireCarrierReachable(carrier.getPerson());
        UnitAtemschutzSettings settings = settingsService.ensureSettings(unitId);
        List<String> ccEmails = unionStaffEmails(unitId, settings, items);
        SendAttempt attempt = sendCarrierDigest(unitId, carrier, items, ccEmails);
        if (attempt == SendAttempt.SENT) {
            logItems(carrier, items, countAsCarrierNotified(carrier.getPerson()));
        }
        return switch (attempt) {
            case SENT -> ManualReminderResult.sent(
                    1,
                    items.size() == 1
                            ? "Erinnerung für " + items.get(0).category().getFitnessType().label()
                                    + " wurde per E-Mail gesendet."
                            : "Erinnerung mit " + items.size() + " Nachweisen wurde in einer E-Mail gesendet.");
            case TEST_SKIPPED -> ManualReminderResult.skipped("Im Testmodus wurde keine E-Mail versendet.");
            case FAILED -> throw new IllegalArgumentException("E-Mail konnte nicht gesendet werden.");
            case SKIPPED -> throw new IllegalArgumentException(
                    "Erinnerung konnte nicht gesendet werden. Bitte E-Mail-Vorlage und SMTP prüfen.");
        };
    }

    @Transactional
    public ManualReminderResult sendManualForCarriers(long unitId, List<Long> carrierIds) {
        requireMailReady(unitId);
        if (carrierIds == null || carrierIds.isEmpty()) {
            throw new IllegalArgumentException("Bitte zuerst Geräteträger in der Tabelle ankreuzen.");
        }
        UnitAtemschutzSettings settings = settingsService.ensureSettings(unitId);
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(carrierIds);
        Map<Long, CarrierOverview> byId = atemschutzService
                .listCarrierOverviews(unitId, "all")
                .carriers()
                .stream()
                .collect(Collectors.toMap(
                        row -> row.carrier().getId(), row -> row, (left, right) -> left));
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        boolean testSkipped = false;
        for (Long carrierId : uniqueIds) {
            if (carrierId == null) {
                skipped++;
                continue;
            }
            CarrierOverview overview = byId.get(carrierId);
            if (overview == null
                    || !PersonMembership.isCurrentlyMember(overview.carrier().getPerson())) {
                skipped++;
                continue;
            }
            List<ReminderItem> items = collectEligibleItems(overview.summaries());
            if (items.isEmpty()) {
                skipped++;
                continue;
            }
            if (!mayNotifyPerson(overview.carrier().getPerson())
                    || resolvePersonEmail(overview.carrier().getPerson()) == null) {
                skipped++;
                continue;
            }
            List<String> ccEmails = unionStaffEmails(unitId, settings, items);
            SendAttempt attempt = sendCarrierDigest(unitId, overview.carrier(), items, ccEmails);
            switch (attempt) {
                case SENT -> {
                    logItems(overview.carrier(), items, countAsCarrierNotified(overview.carrier().getPerson()));
                    sent++;
                }
                case FAILED -> failed++;
                case TEST_SKIPPED -> {
                    testSkipped = true;
                    skipped++;
                }
                case SKIPPED -> skipped++;
            }
        }
        if (sent == 0 && failed == 0 && testSkipped) {
            return ManualReminderResult.skipped("Im Testmodus wurde keine E-Mail versendet.");
        }
        if (sent == 0 && failed == 0) {
            return ManualReminderResult.skipped(
                    "Keine Erinnerung gesendet. Bei der Auswahl liegt aktuell keine Warnung oder kein Ablauf vor, "
                            + "oder es fehlt eine erreichbare E-Mail-Adresse.");
        }
        StringBuilder message = new StringBuilder();
        message.append(sent).append(" Erinnerung(en) gesendet.");
        if (skipped > 0) {
            message.append(" ").append(skipped).append(" übersprungen.");
        }
        if (failed > 0) {
            message.append(" ").append(failed).append(" fehlgeschlagen.");
        }
        return new ManualReminderResult(sent, skipped, failed, failed == 0, message.toString());
    }

    private void requireMailReady(long unitId) {
        if (!unitMailService.canSendForUnit(unitId)) {
            throw new IllegalArgumentException(
                    "SMTP der Einheit ist nicht konfiguriert (Admin → Einheit → Schnittstellen).");
        }
    }

    private static void requireCarrierInUnit(AtemschutzCarrier carrier, long unitId) {
        if (carrier.getUnit() == null || !carrier.getUnit().getId().equals(unitId)) {
            throw new IllegalArgumentException("Geräteträger nicht gefunden.");
        }
    }

    private void requireCarrierReachable(Person person) {
        if (!mayNotifyPerson(person)) {
            throw new IllegalArgumentException(
                    "Diese Person hat E-Mail-Benachrichtigungen für Atemschutz deaktiviert.");
        }
        if (resolvePersonEmail(person) == null) {
            throw new IllegalArgumentException("Keine E-Mail-Adresse hinterlegt.");
        }
    }

    private List<ReminderItem> collectEligibleItems(Map<AtemschutzFitnessType, FitnessStatusView> summaries) {
        List<ReminderItem> items = new ArrayList<>();
        if (summaries == null) {
            return items;
        }
        for (AtemschutzNotificationCategory category : AtemschutzNotificationCategory.values()) {
            FitnessStatusView fitness = summaries.get(category.getFitnessType());
            AtemschutzReminderMailKind mailKind = mailKindFor(fitness);
            if (mailKind == null || fitness.validUntil() == null) {
                continue;
            }
            items.add(new ReminderItem(category, mailKind, fitness.validUntil()));
        }
        return items;
    }

    private List<String> unionStaffEmails(
            long unitId, UnitAtemschutzSettings settings, List<ReminderItem> items) {
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        for (ReminderItem item : items) {
            emails.addAll(collectStaffEmails(unitId, settings, item.category()));
        }
        return new ArrayList<>(emails);
    }

    private SendAttempt sendCarrierDigest(
            long unitId, AtemschutzCarrier carrier, List<ReminderItem> items, List<String> staffEmails) {
        if (items == null || items.isEmpty()) {
            return SendAttempt.SKIPPED;
        }
        Person person = carrier.getPerson();
        if (items.size() == 1) {
            ReminderItem item = items.get(0);
            return sendReminder(
                    unitId,
                    item.category(),
                    item.mailKind(),
                    person,
                    item.validUntil(),
                    true,
                    staffEmails);
        }
        String subject = digestSubject(items);
        String body = textToHtml(digestBody(person, items));
        String carrierEmail = mayNotifyPerson(person) ? resolvePersonEmail(person) : null;
        List<String> allowedStaff = staffEmails.stream().filter(this::mayNotifyEmail).toList();
        if (carrierEmail != null) {
            List<String> cc = allowedStaff.stream()
                    .filter(email -> !email.equalsIgnoreCase(carrierEmail))
                    .toList();
            return dispatchMail(unitId, carrierEmail, cc, subject, body, person, items.get(0).category());
        }
        if (!allowedStaff.isEmpty()) {
            String to = allowedStaff.get(0);
            List<String> cc = allowedStaff.size() > 1 ? allowedStaff.subList(1, allowedStaff.size()) : List.of();
            return dispatchMail(unitId, to, cc, subject, body, person, items.get(0).category());
        }
        return SendAttempt.SKIPPED;
    }

    private static String digestSubject(List<ReminderItem> items) {
        boolean overdue = items.stream().anyMatch(item -> item.mailKind() == AtemschutzReminderMailKind.ABGELAUFEN);
        String names = items.stream()
                .map(item -> item.category().getLabel())
                .collect(Collectors.joining(", "));
        if (overdue) {
            return "ACHTUNG: Atemschutz-Nachweise prüfen (" + names + ")";
        }
        return "Erinnerung: Atemschutz-Nachweise (" + names + ")";
    }

    private static String digestBody(Person person, List<ReminderItem> items) {
        String firstName = person.getFirstName() != null ? person.getFirstName().trim() : "";
        String lastName = person.getLastName() != null ? person.getLastName().trim() : "";
        StringBuilder body = new StringBuilder();
        body.append("Hallo ").append(firstName);
        if (!lastName.isBlank()) {
            if (!firstName.isBlank()) {
                body.append(' ');
            }
            body.append(lastName);
        }
        body.append(",\n\n");
        body.append("für Sie sind aktuell folgende Atemschutz-Nachweise relevant:\n\n");
        for (ReminderItem item : items) {
            String expiry = item.validUntil() != null ? DATE_FMT.format(item.validUntil()) : "—";
            String status = item.mailKind() == AtemschutzReminderMailKind.ABGELAUFEN ? "überfällig" : "bald fällig";
            body.append("• ")
                    .append(item.category().getFitnessType().label())
                    .append(" — gültig bis ")
                    .append(expiry)
                    .append(" (")
                    .append(status)
                    .append(")\n");
        }
        body.append("\nBitte kümmern Sie sich zeitnah um die Verlängerung.\n\n");
        body.append("Mit freundlichen Grüßen\nIhre Feuerwehr");
        return body.toString();
    }

    private boolean countAsCarrierNotified(Person person) {
        if (!mayNotifyPerson(person) || resolvePersonEmail(person) == null) {
            return false;
        }
        if (testModeService.isEnabled() && TestModeEmailContext.isSet()) {
            return TestModeEmailContext.getDelivery() == TestModeEmailDelivery.CONFIGURED;
        }
        return true;
    }

    private static AtemschutzReminderMailKind mailKindFor(FitnessStatusView fitness) {
        if (fitness == null) {
            return null;
        }
        return switch (fitness.level()) {
            case WARN -> AtemschutzReminderMailKind.WARNUNG;
            case OVERDUE -> AtemschutzReminderMailKind.ABGELAUFEN;
            default -> null;
        };
    }

    private void logItems(AtemschutzCarrier carrier, List<ReminderItem> items, boolean carrierNotified) {
        for (ReminderItem item : items) {
            logReminder(carrier, item, carrierNotified);
        }
    }

    private void logReminder(AtemschutzCarrier carrier, ReminderItem item, boolean carrierNotified) {
        Instant now = Instant.now();
        AtemschutzReminderLog logEntry = reminderLogRepository
                .findByCarrierIdAndFitnessTypeAndMailKindAndValidUntil(
                        carrier.getId(),
                        item.category().getFitnessType(),
                        item.mailKind(),
                        item.validUntil())
                .orElseGet(() -> {
                    AtemschutzReminderLog created = new AtemschutzReminderLog();
                    created.setCarrier(carrier);
                    created.setFitnessType(item.category().getFitnessType());
                    created.setMailKind(item.mailKind());
                    created.setValidUntil(item.validUntil());
                    return created;
                });
        if (carrierNotified || logEntry.getId() == null) {
            logEntry.setSentAt(now);
        }
        if (carrierNotified) {
            logEntry.setCarrierNotified(true);
        }
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

        String carrierEmail = notifyCarriers && mayNotifyPerson(person) ? resolvePersonEmail(person) : null;
        List<String> allowedStaff = staffEmails.stream().filter(this::mayNotifyEmail).toList();
        if (carrierEmail != null) {
            List<String> cc = allowedStaff.stream()
                    .filter(email -> !email.equalsIgnoreCase(carrierEmail))
                    .toList();
            return dispatchMail(unitId, carrierEmail, cc, subject, body, person, category);
        }
        if (!allowedStaff.isEmpty()) {
            String to = allowedStaff.get(0);
            List<String> cc = allowedStaff.size() > 1 ? allowedStaff.subList(1, allowedStaff.size()) : List.of();
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
        String effectiveTo = toEmail;
        List<String> effectiveCc = ccEmails != null ? ccEmails : List.of();
        if (testModeService.isEnabled() && TestModeEmailContext.isSet()) {
            TestModeEmailDelivery delivery = TestModeEmailContext.getDelivery();
            if (delivery == TestModeEmailDelivery.NONE) {
                log.info(
                        "Testmodus: Atemschutz-Erinnerung nicht gesendet (Auswahl: keine E-Mail, Einheit {}).",
                        unitId);
                return SendAttempt.TEST_SKIPPED;
            }
            if (delivery == TestModeEmailDelivery.SELF) {
                String actorEmail = TestModeEmailContext.getActorEmail();
                if (actorEmail == null || actorEmail.isBlank()) {
                    log.warn(
                            "Testmodus: Atemschutz-Erinnerung nicht gesendet — keine Login-E-Mail (Einheit {}).",
                            unitId);
                    return SendAttempt.TEST_SKIPPED;
                }
                effectiveTo = actorEmail.trim();
                effectiveCc = List.of();
            }
        }
        Optional<String> error = unitMailService.sendHtmlMail(unitId, effectiveTo, effectiveCc, subject, body);
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
                userRepository
                        .findById(userId)
                        .filter(user -> userNotificationPreferenceService.isEmailEnabled(userId, UserNotificationTopic.ATEMSCHUTZ))
                        .map(this::resolveUserEmail)
                        .ifPresent(emails::add);
            }
        }
        boolean testData = testModeService.isEnabled();
        for (Long personId : settingsService.ccPersonIds(settings, category)) {
            personRepository
                    .findActiveById(personId, testData)
                    .filter(p -> p.getUnit().getId().equals(unitId))
                    .filter(this::mayNotifyPerson)
                    .map(this::resolvePersonEmail)
                    .ifPresent(emails::add);
        }
        return new ArrayList<>(emails);
    }

    private boolean mayNotifyPerson(Person person) {
        return userNotificationPreferenceService.isEmailEnabledForPerson(person, UserNotificationTopic.ATEMSCHUTZ);
    }

    private boolean mayNotifyEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return userRepository
                .findByLoginEmailIgnoreCaseWithUnit(email.trim())
                .map(user -> userNotificationPreferenceService.isEmailEnabledForUser(user, UserNotificationTopic.ATEMSCHUTZ))
                .orElse(true);
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
        FAILED,
        TEST_SKIPPED
    }

    private record ReminderItem(
            AtemschutzNotificationCategory category,
            AtemschutzReminderMailKind mailKind,
            LocalDate validUntil) {}

    public record ReminderRunResult(int sent, int skipped, int failed) {}

    public record ManualReminderResult(int sent, int skipped, int failed, boolean success, String message) {
        static ManualReminderResult sent(int count, String message) {
            return new ManualReminderResult(count, 0, 0, true, message);
        }

        static ManualReminderResult skipped(String message) {
            return new ManualReminderResult(0, 1, 0, true, message);
        }
    }
}
