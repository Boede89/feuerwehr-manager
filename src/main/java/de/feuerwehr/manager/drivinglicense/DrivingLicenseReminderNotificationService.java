package de.feuerwehr.manager.drivinglicense;

import de.feuerwehr.manager.mail.UnitMailService;
import de.feuerwehr.manager.notification.UserNotificationPreferenceService;
import de.feuerwehr.manager.notification.UserNotificationTopic;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrivingLicenseReminderNotificationService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY);

    private final UnitRepository unitRepository;
    private final ModuleSettingsService moduleSettingsService;
    private final TestModeService testModeService;
    private final DrivingLicenseService drivingLicenseService;
    private final DrivingLicenseSettingsService settingsService;
    private final DrivingLicenseReminderLogRepository reminderLogRepository;
    private final UnitMailService unitMailService;
    private final UserRepository userRepository;
    private final UserNotificationPreferenceService userNotificationPreferenceService;

    @Transactional
    public ReminderRunResult processAllUnits() {
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        for (Unit unit : unitRepository.findActiveVisible(testModeService.isEnabled())) {
            if (!moduleSettingsService.isEnabled(AppModule.PERSONAL, unit.getId())) {
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
        UnitDrivingLicenseSettings settings = settingsService.ensureSettings(unitId);
        List<Long> staffUserIds = settingsService.parseNotificationUserIds(settings);
        if (staffUserIds.isEmpty() && !settings.isNotifyPerson()) {
            return new ReminderRunResult(0, 0, 0);
        }
        DrivingLicenseService.OverviewPage page = drivingLicenseService.listOverview(unitId, "all");
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        for (DrivingLicenseService.OverviewRow row : page.rows()) {
            if (row.level() != DrivingLicenseStatusLevel.WARN && row.level() != DrivingLicenseStatusLevel.OVERDUE) {
                continue;
            }
            if (row.license() == null || row.nextDueOn() == null) {
                skipped++;
                continue;
            }
            DrivingLicenseReminderMailKind kind = row.level() == DrivingLicenseStatusLevel.OVERDUE
                    ? DrivingLicenseReminderMailKind.OVERDUE
                    : DrivingLicenseReminderMailKind.WARN;
            Optional<DrivingLicenseReminderLog> existing = reminderLogRepository.findByLicenseIdAndMailKindAndNextDueOn(
                    row.license().getId(), kind, row.nextDueOn());
            if (existing.isPresent()) {
                skipped++;
                continue;
            }
            List<String> staffEmails = collectStaffEmails(staffUserIds);
            boolean notifyPerson = settings.isNotifyPerson();
            String personEmail = resolvePersonEmail(row.person());
            if (staffEmails.isEmpty() && !(notifyPerson && personEmail != null)) {
                skipped++;
                continue;
            }
            String subject = kind == DrivingLicenseReminderMailKind.OVERDUE
                    ? "Führerscheinkontrolle überfällig: " + row.person().displayName()
                    : "Führerscheinkontrolle bald fällig: " + row.person().displayName();
            String body = buildBody(row, kind);
            boolean anySent = false;
            boolean anyFailed = false;
            boolean personNotified = false;
            if (notifyPerson && personEmail != null) {
                Optional<String> err = unitMailService.sendHtmlMail(unitId, personEmail, subject, body);
                if (err.isEmpty()) {
                    anySent = true;
                    personNotified = true;
                } else {
                    anyFailed = true;
                    log.warn("Führerschein-Erinnerung an {} fehlgeschlagen: {}", personEmail, err.get());
                }
            }
            for (String email : staffEmails) {
                Optional<String> err = unitMailService.sendHtmlMail(unitId, email, subject, body);
                if (err.isEmpty()) {
                    anySent = true;
                } else {
                    anyFailed = true;
                    log.warn("Führerschein-Erinnerung an {} fehlgeschlagen: {}", email, err.get());
                }
            }
            if (anySent) {
                DrivingLicenseReminderLog logEntry = new DrivingLicenseReminderLog();
                logEntry.setLicense(row.license());
                logEntry.setMailKind(kind);
                logEntry.setNextDueOn(row.nextDueOn());
                logEntry.setSentAt(Instant.now());
                logEntry.setPersonNotified(personNotified);
                reminderLogRepository.save(logEntry);
                sent++;
            } else if (anyFailed) {
                failed++;
            } else {
                skipped++;
            }
        }
        return new ReminderRunResult(sent, skipped, failed);
    }

    private List<String> collectStaffEmails(List<Long> userIds) {
        Set<String> emails = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            userRepository.findById(userId).ifPresent(user -> {
                if (!userNotificationPreferenceService.isEmailEnabled(user.getId(), UserNotificationTopic.FUEHRERSCHEIN)) {
                    return;
                }
                String email = resolveUserEmail(user);
                if (email != null) {
                    emails.add(email);
                }
            });
        }
        return new ArrayList<>(emails);
    }

    private String resolveUserEmail(User user) {
        if (user.getLoginEmail() != null && !user.getLoginEmail().isBlank()) {
            return user.getLoginEmail().trim();
        }
        return null;
    }

    private String resolvePersonEmail(Person person) {
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

    private static String buildBody(DrivingLicenseService.OverviewRow row, DrivingLicenseReminderMailKind kind) {
        String due = row.nextDueOn() != null ? DATE_FMT.format(row.nextDueOn()) : "—";
        String status = kind == DrivingLicenseReminderMailKind.OVERDUE ? "überfällig" : "bald fällig";
        return "<p>Die Führerscheinkontrolle für <strong>"
                + escape(row.person().displayName())
                + "</strong> ist <strong>"
                + status
                + "</strong>.</p>"
                + "<p>Nächste Fälligkeit: <strong>"
                + due
                + "</strong></p>"
                + "<p>Bitte den Originalführerschein vorzeigen lassen und die Kontrolle im Feuerwehr-Manager dokumentieren."
                + " Es wird keine Kopie des Führerscheins gespeichert.</p>";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public record ReminderRunResult(int sent, int skipped, int failed) {}
}
