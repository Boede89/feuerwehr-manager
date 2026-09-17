package de.feuerwehr.manager.drivinglicense;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.user.UserRepository;
import de.feuerwehr.manager.util.PersonMembership;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DrivingLicenseService {

    private final DrivingLicenseRepository licenseRepository;
    private final DrivingLicenseCheckRepository checkRepository;
    private final DrivingLicenseSettingsService settingsService;
    private final PersonalService personalService;
    private final UserRepository userRepository;

    @Transactional
    public DrivingLicense ensureLicense(long personId) {
        return licenseRepository.findByPersonId(personId).orElseGet(() -> {
            Person person = personalService.requirePerson(personId);
            DrivingLicense license = new DrivingLicense();
            license.setPerson(person);
            license.setPresence(DrivingLicensePresence.UNKNOWN);
            return licenseRepository.save(license);
        });
    }

    @Transactional(readOnly = true)
    public Optional<DrivingLicense> findLicense(long personId) {
        return licenseRepository.findByPersonId(personId);
    }

    @Transactional
    public PersonLicenseView loadPersonView(long personId) {
        Person person = personalService.requirePerson(personId);
        UnitDrivingLicenseSettings settings = settingsService.ensureSettings(person.getUnit().getId());
        DrivingLicense license = licenseRepository.findByPersonId(personId).orElse(null);
        List<DrivingLicenseCheck> checks = license == null
                ? List.of()
                : checkRepository.findByLicenseIdOrderByCheckedOnDesc(license.getId());
        LocalDate today = LocalDate.now();
        return new PersonLicenseView(
                person,
                license,
                checks,
                computeLevel(license, settings.getWarnDays(), today),
                settings.getIntervalMonths(),
                settings.getWarnDays(),
                DrivingLicenseClass.all());
    }

    @Transactional
    public DrivingLicense saveStammdaten(
            long personId,
            DrivingLicensePresence presence,
            String[] classCodes,
            LocalDate issuedOn,
            String numberSuffix,
            String restrictions,
            boolean selfReportAcknowledged) {
        if (presence == null) {
            presence = DrivingLicensePresence.UNKNOWN;
        }
        DrivingLicense license = ensureLicense(personId);
        license.setPresence(presence);
        if (presence == DrivingLicensePresence.NO) {
            license.setClassesCsv(null);
            license.setIssuedOn(null);
            license.setNumberSuffix(null);
            license.setRestrictions(null);
            license.setNextDueOn(null);
            license.setSelfReportAcknowledged(false);
        } else {
            license.setClassesCsv(DrivingLicenseClass.toCsvFromCodes(classCodes));
            license.setIssuedOn(issuedOn);
            license.setNumberSuffix(normalizeSuffix(numberSuffix));
            license.setRestrictions(trimToNull(restrictions));
            license.setSelfReportAcknowledged(selfReportAcknowledged);
            if (presence == DrivingLicensePresence.UNKNOWN && license.getNextDueOn() == null) {
                // keine automatische Fälligkeit ohne bestätigten Führerschein
            }
        }
        return licenseRepository.save(license);
    }

    @Transactional
    public DrivingLicenseCheck recordCheck(
            long personId,
            LocalDate checkedOn,
            DrivingLicenseCheckResult result,
            String[] classCodes,
            String notes,
            AppUserDetails actor) {
        if (checkedOn == null) {
            throw new IllegalArgumentException("Bitte ein Kontrolldatum angeben.");
        }
        if (result == null) {
            throw new IllegalArgumentException("Bitte ein Prüfergebnis wählen.");
        }
        DrivingLicense license = ensureLicense(personId);
        if (license.getPresence() == DrivingLicensePresence.NO) {
            throw new IllegalArgumentException("Für Personen ohne Führerschein ist keine Kontrolle vorgesehen.");
        }
        Person person = license.getPerson();
        UnitDrivingLicenseSettings settings = settingsService.ensureSettings(person.getUnit().getId());
        LocalDate nextDue = checkedOn.plusMonths(settings.getIntervalMonths());
        String classesCsv = DrivingLicenseClass.toCsvFromCodes(classCodes);
        if (classesCsv == null || classesCsv.isBlank()) {
            classesCsv = license.getClassesCsv();
        } else {
            license.setClassesCsv(classesCsv);
        }
        if (license.getPresence() != DrivingLicensePresence.YES) {
            license.setPresence(DrivingLicensePresence.YES);
        }

        DrivingLicenseCheck check = new DrivingLicenseCheck();
        check.setLicense(license);
        check.setCheckedOn(checkedOn);
        check.setNextDueOn(nextDue);
        check.setResult(result);
        check.setClassesCsv(classesCsv);
        check.setNotes(trimToNull(notes));
        if (actor != null) {
            userRepository.findById(actor.getUserId()).ifPresent(check::setCheckedBy);
            check.setCheckedByDisplayName(actor.getDisplayName());
        }
        checkRepository.save(check);

        if (result == DrivingLicenseCheckResult.INVALID) {
            license.setNextDueOn(checkedOn);
        } else {
            license.setNextDueOn(nextDue);
        }
        licenseRepository.save(license);
        return check;
    }

    @Transactional
    public OverviewPage listOverview(long unitId, String filter) {
        UnitDrivingLicenseSettings settings = settingsService.ensureSettings(unitId);
        int warnDays = settings.getWarnDays();
        LocalDate today = LocalDate.now();
        String normalized = normalizeFilter(filter);

        List<Person> persons = personalService.listPersons(unitId).stream()
                .filter(PersonMembership::isCurrentlyMember)
                .toList();
        Map<Long, DrivingLicense> byPersonId = licenseRepository.findByUnitIdWithPerson(unitId).stream()
                .collect(Collectors.toMap(l -> l.getPerson().getId(), Function.identity(), (a, b) -> a));

        List<OverviewRow> rows = new ArrayList<>();
        int overdue = 0;
        int warn = 0;
        int ok = 0;
        int missing = 0;
        int none = 0;
        for (Person person : persons) {
            DrivingLicense license = byPersonId.get(person.getId());
            DrivingLicenseStatusLevel level = computeLevel(license, warnDays, today);
            switch (level) {
                case OVERDUE -> overdue++;
                case WARN -> warn++;
                case OK -> ok++;
                case MISSING -> missing++;
                case NONE -> none++;
            }
            if (!matchesFilter(normalized, level)) {
                continue;
            }
            rows.add(new OverviewRow(
                    person,
                    license,
                    level,
                    license != null ? license.getNextDueOn() : null,
                    license != null ? license.getClassesCsv() : null,
                    license != null ? license.getPresence() : DrivingLicensePresence.UNKNOWN));
        }
        rows.sort(Comparator
                .comparing((OverviewRow r) -> sortRank(r.level()))
                .thenComparing(r -> r.person().getLastName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> r.person().getFirstName(), String.CASE_INSENSITIVE_ORDER));
        return new OverviewPage(
                rows,
                new OverviewStats(persons.size(), overdue, warn, ok, missing, none),
                settings.getIntervalMonths(),
                warnDays,
                normalized);
    }

    public static DrivingLicenseStatusLevel computeLevel(
            DrivingLicense license, int warnDays, LocalDate today) {
        if (license == null || license.getPresence() == DrivingLicensePresence.UNKNOWN) {
            return DrivingLicenseStatusLevel.MISSING;
        }
        if (license.getPresence() == DrivingLicensePresence.NO) {
            return DrivingLicenseStatusLevel.NONE;
        }
        LocalDate due = license.getNextDueOn();
        if (due == null) {
            return DrivingLicenseStatusLevel.MISSING;
        }
        if (due.isBefore(today)) {
            return DrivingLicenseStatusLevel.OVERDUE;
        }
        long days = ChronoUnit.DAYS.between(today, due);
        if (days <= warnDays) {
            return DrivingLicenseStatusLevel.WARN;
        }
        return DrivingLicenseStatusLevel.OK;
    }

    private static boolean matchesFilter(String filter, DrivingLicenseStatusLevel level) {
        return switch (filter) {
            case "overdue" -> level == DrivingLicenseStatusLevel.OVERDUE;
            case "warn" -> level == DrivingLicenseStatusLevel.WARN;
            case "ok" -> level == DrivingLicenseStatusLevel.OK;
            case "missing" -> level == DrivingLicenseStatusLevel.MISSING;
            case "none" -> level == DrivingLicenseStatusLevel.NONE;
            default -> true;
        };
    }

    private static String normalizeFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return "all";
        }
        return switch (filter.trim().toLowerCase()) {
            case "overdue", "warn", "ok", "missing", "none" -> filter.trim().toLowerCase();
            default -> "all";
        };
    }

    private static int sortRank(DrivingLicenseStatusLevel level) {
        return switch (level) {
            case OVERDUE -> 0;
            case WARN -> 1;
            case MISSING -> 2;
            case OK -> 3;
            case NONE -> 4;
        };
    }

    private static String normalizeSuffix(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.length() > 16) {
            throw new IllegalArgumentException("Die Führerscheinnummer darf höchstens 16 Zeichen haben.");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record PersonLicenseView(
            Person person,
            DrivingLicense license,
            List<DrivingLicenseCheck> checks,
            DrivingLicenseStatusLevel level,
            int intervalMonths,
            int warnDays,
            List<DrivingLicenseClass> availableClasses) {}

    public record OverviewRow(
            Person person,
            DrivingLicense license,
            DrivingLicenseStatusLevel level,
            LocalDate nextDueOn,
            String classesCsv,
            DrivingLicensePresence presence) {}

    public record OverviewStats(int total, int overdue, int warn, int ok, int missing, int none) {}

    public record OverviewPage(
            List<OverviewRow> rows,
            OverviewStats stats,
            int intervalMonths,
            int warnDays,
            String activeFilter) {}
}
