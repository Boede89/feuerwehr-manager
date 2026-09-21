package de.feuerwehr.manager.user;

import de.feuerwehr.manager.dsgvo.AuditEventType;
import de.feuerwehr.manager.dsgvo.AuditService;
import de.feuerwehr.manager.mail.AccountMailService;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.personal.PersonStatus;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    public record FieldDiff(String field, String label, String registrationValue, String personValue) {}

    public record ApprovalPreview(
            User user,
            Person matchedPerson,
            List<FieldDiff> differences,
            boolean personAlreadyLinked,
            String warning) {}

    public record FieldChoice(String field, String source) {}

    public record RegistrationEdits(
            String firstName, String lastName, String email, LocalDate birthdate) {}

    public record PersonOption(
            long id,
            String firstName,
            String lastName,
            String email,
            String birthdate,
            String displayName,
            boolean alreadyLinked) {}

    private final UserRepository userRepository;
    private final UnitRepository unitRepository;
    private final PersonRepository personRepository;
    private final PersonalService personalService;
    private final UserManagementService userManagementService;
    private final AccountMailService accountMailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AccessControlService accessControlService;
    private final TestModeService testModeService;

    @Transactional
    public User register(
            String firstName,
            String lastName,
            String email,
            LocalDate birthdate,
            long unitId,
            HttpServletRequest request) {
        String first = requireName(firstName, "Vorname");
        String last = requireName(lastName, "Nachname");
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            throw new IllegalArgumentException("Bitte eine gültige E-Mail-Adresse angeben.");
        }
        if (birthdate == null) {
            throw new IllegalArgumentException("Bitte das Geburtsdatum angeben.");
        }
        if (birthdate.isAfter(LocalDate.now().minusYears(10))) {
            throw new IllegalArgumentException("Bitte ein plausibles Geburtsdatum angeben.");
        }
        Unit unit = unitRepository
                .findById(unitId)
                .filter(Unit::isActive)
                .filter(u -> testModeService.isEnabled() || !u.isTestData())
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        if (!unit.isSelfRegistrationEnabled()) {
            throw new IllegalArgumentException("Die Selbstregistrierung ist für diese Einheit deaktiviert.");
        }
        if (userRepository.findByLoginEmailIgnoreCaseExcludingId(normalizedEmail, null).isPresent()) {
            throw new IllegalArgumentException("Für diese E-Mail-Adresse existiert bereits ein Benutzerkonto.");
        }

        String username = userManagementService.allocateUniqueUsername(first, last);
        User user = new User();
        user.setUsername(username);
        user.setFirstName(first);
        user.setLastName(last);
        user.setBirthdate(birthdate);
        user.setDisplayName(Person.formatDisplayName(first, last));
        user.setLoginEmail(normalizedEmail);
        user.setRole(UserRole.USER);
        user.setUnit(unit);
        user.setActive(false);
        user.setRegistrationPending(true);
        user.setPasswordHash(null);
        User saved = userRepository.save(user);
        auditService.record(
                AuditEventType.USER_CREATED,
                null,
                saved.getId(),
                request,
                "Selbstregistrierung (Freigabe ausstehend)");
        try {
            accountMailService.notifyAdminsRegistrationPending(saved, unit.getId());
        } catch (Exception ignored) {
            // Registrierung bleibt gültig auch ohne Admin-Mail
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public long countPendingForUnit(long unitId) {
        return userRepository.countPendingRegistrationsByUnitId(unitId);
    }

    @Transactional(readOnly = true)
    public ApprovalPreview previewApproval(long userId, AppUserDetails actor) {
        User user = requirePendingUser(userId, actor);
        Person matched = findBestPersonMatch(user).orElse(null);
        List<FieldDiff> diffs = matched == null ? List.of() : computeDifferences(user, matched);
        boolean alreadyLinked = matched != null
                && matched.getUser() != null
                && matched.getUser().getId() != null
                && !matched.getUser().getId().equals(user.getId());
        String warning = null;
        if (matched == null) {
            warning =
                    "Im Personal wurde keine passende Person gefunden. Sie können eine bestehende Person wählen oder eine neue anlegen.";
        } else if (alreadyLinked) {
            warning =
                    "Die vorgeschlagene Person ist bereits mit einem anderen Benutzerkonto verknüpft. Bitte wählen Sie eine andere Person oder legen Sie eine neue an.";
        }
        return new ApprovalPreview(user, matched, diffs, alreadyLinked, warning);
    }

    @Transactional(readOnly = true)
    public List<PersonOption> listPersonOptions(long userId, AppUserDetails actor) {
        User user = requirePendingUser(userId, actor);
        if (user.getUnit() == null) {
            return List.of();
        }
        long unitId = user.getUnit().getId();
        List<PersonOption> options = new ArrayList<>();
        for (Person person : personalService.listPersons(unitId)) {
            boolean linked = person.getUser() != null
                    && person.getUser().getId() != null
                    && !person.getUser().getId().equals(user.getId());
            options.add(new PersonOption(
                    person.getId(),
                    person.getFirstName(),
                    person.getLastName(),
                    person.getEmail(),
                    formatDate(person.getBirthdate()),
                    person.displayName(),
                    linked));
        }
        return options;
    }

    @Transactional
    public String approve(
            long userId,
            Long personId,
            RegistrationEdits edits,
            List<FieldChoice> choices,
            AppUserDetails actor,
            HttpServletRequest request) {
        User user = requirePendingUser(userId, actor);
        if (user.getUnit() == null) {
            throw new IllegalArgumentException("Dem Benutzer ist keine Einheit zugeordnet.");
        }
        long unitId = user.getUnit().getId();
        if (!accountMailService.canSendMailForUnit(unitId)) {
            throw new IllegalArgumentException(
                    "SMTP der Einheit ist nicht konfiguriert. Freischaltung ohne E-Mail-Versand nicht möglich.");
        }

        applyRegistrationEdits(user, edits);

        Person person;
        if (personId != null && personId > 0) {
            person = personalService.requirePerson(personId);
            if (person.getUnit() == null || person.getUnit().getId() != unitId) {
                throw new IllegalArgumentException("Person gehört nicht zur Einheit des Benutzers.");
            }
            if (person.getUser() != null
                    && person.getUser().getId() != null
                    && !person.getUser().getId().equals(user.getId())) {
                throw new IllegalArgumentException(
                        "Die Person ist bereits mit einem anderen Benutzerkonto verknüpft.");
            }
            applyFieldChoices(user, person, choices);
        } else {
            person = createPersonFromRegistration(user);
        }

        person.setUser(user);
        if (person.getEmail() == null || person.getEmail().isBlank()) {
            person.setEmail(user.getLoginEmail());
        }
        personRepository.save(person);

        String password = userManagementService.generateNumericPassword();
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setActive(true);
        user.setRegistrationPending(false);
        user.setMustChangePassword(true);
        user.setDisplayName(Person.formatDisplayName(user.getFirstName(), user.getLastName()));
        User saved = userRepository.save(user);

        Optional<String> mailError =
                accountMailService.sendPasswordNotification(saved, unitId, password, false);
        auditService.record(
                AuditEventType.USER_UPDATED,
                actor.getUserId(),
                saved.getId(),
                request,
                "Selbstregistrierung freigeschaltet");
        if (mailError.isPresent()) {
            return "Benutzer freigeschaltet, aber E-Mail fehlgeschlagen: " + mailError.get();
        }
        return "Benutzer freigeschaltet. Zugangsdaten wurden per E-Mail versendet.";
    }

    private void applyRegistrationEdits(User user, RegistrationEdits edits) {
        if (edits == null) {
            return;
        }
        String first = requireName(edits.firstName(), "Vorname");
        String last = requireName(edits.lastName(), "Nachname");
        String email = normalizeEmail(edits.email());
        if (email == null) {
            throw new IllegalArgumentException("Bitte eine gültige E-Mail-Adresse angeben.");
        }
        if (edits.birthdate() == null) {
            throw new IllegalArgumentException("Bitte das Geburtsdatum angeben.");
        }
        if (edits.birthdate().isAfter(LocalDate.now().minusYears(10))) {
            throw new IllegalArgumentException("Bitte ein plausibles Geburtsdatum angeben.");
        }
        if (userRepository.findByLoginEmailIgnoreCaseExcludingId(email, user.getId()).isPresent()) {
            throw new IllegalArgumentException("Für diese E-Mail-Adresse existiert bereits ein Benutzerkonto.");
        }
        user.setFirstName(first);
        user.setLastName(last);
        user.setLoginEmail(email);
        user.setBirthdate(edits.birthdate());
        user.setDisplayName(Person.formatDisplayName(first, last));
    }

    @Transactional
    public String reject(long userId, AppUserDetails actor, HttpServletRequest request) {
        User user = requirePendingUser(userId, actor);
        String email = user.getLoginEmail();
        String displayName = user.getDisplayName();
        Long unitId = user.getUnit() != null ? user.getUnit().getId() : null;

        Optional<String> mailError = Optional.empty();
        if (email != null && !email.isBlank() && unitId != null) {
            mailError = accountMailService.sendRegistrationRejected(user, unitId);
        }

        userManagementService.deleteUserByAdmin(userId, actor, request);
        if (mailError.isPresent()) {
            return "Registrierung abgelehnt. E-Mail fehlgeschlagen: " + mailError.get();
        }
        if (email == null || email.isBlank()) {
            return "Registrierung von " + displayName + " abgelehnt (keine E-Mail hinterlegt).";
        }
        return "Registrierung abgelehnt. Der Antragsteller wurde per E-Mail informiert.";
    }

    private User requirePendingUser(long userId, AppUserDetails actor) {
        User user = userRepository
                .findByIdWithUnit(userId)
                .orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden."));
        if (user.getAnonymizedAt() != null) {
            throw new IllegalArgumentException("Konto wurde gelöscht.");
        }
        if (!user.isRegistrationPending() || user.isActive()) {
            throw new IllegalArgumentException("Für diesen Benutzer steht keine Freigabe aus.");
        }
        accessControlService.requireCanManageUser(actor, user);
        return user;
    }

    private Optional<Person> findBestPersonMatch(User user) {
        if (user.getUnit() == null) {
            return Optional.empty();
        }
        String first = normalizeName(user.getFirstName());
        String last = normalizeName(user.getLastName());
        String email = user.getLoginEmail() != null ? user.getLoginEmail().trim() : null;
        LocalDate birth = user.getBirthdate();

        Person best = null;
        int bestScore = 0;
        for (Person person : personalService.listPersons(user.getUnit().getId())) {
            int score = scorePersonMatch(person, first, last, email, birth);
            if (score > bestScore) {
                bestScore = score;
                best = person;
            }
        }
        // Mindestens Name (auch vertauscht) oder E-Mail muss passen.
        if (best == null || bestScore < 40) {
            return Optional.empty();
        }
        return Optional.of(best);
    }

    private static int scorePersonMatch(
            Person person, String first, String last, String email, LocalDate birth) {
        int score = 0;
        String personFirst = normalizeName(person.getFirstName());
        String personLast = normalizeName(person.getLastName());

        boolean nameExact = !first.isEmpty()
                && !last.isEmpty()
                && first.equals(personFirst)
                && last.equals(personLast);
        boolean nameSwapped = !first.isEmpty()
                && !last.isEmpty()
                && first.equals(personLast)
                && last.equals(personFirst);
        if (nameExact) {
            score += 50;
        } else if (nameSwapped) {
            score += 40;
        }

        if (email != null
                && !email.isBlank()
                && (emailEquals(person.getEmail(), email) || emailEquals(person.getEmailPrivate(), email))) {
            score += 100;
        }

        if (birth != null && birth.equals(person.getBirthdate())) {
            score += 20;
        }
        return score;
    }

    private static String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.GERMAN);
    }

    private List<FieldDiff> computeDifferences(User user, Person person) {
        List<FieldDiff> diffs = new ArrayList<>();
        addDiff(diffs, "firstName", "Vorname", user.getFirstName(), person.getFirstName());
        addDiff(diffs, "lastName", "Nachname", user.getLastName(), person.getLastName());
        addDiff(diffs, "email", "E-Mail", user.getLoginEmail(), person.getEmail());
        addDiff(
                diffs,
                "birthdate",
                "Geburtsdatum",
                formatDate(user.getBirthdate()),
                formatDate(person.getBirthdate()));
        return diffs;
    }

    private void applyFieldChoices(User user, Person person, List<FieldChoice> choices) {
        Map<String, String> sourceByField = new LinkedHashMap<>();
        if (choices != null) {
            for (FieldChoice choice : choices) {
                if (choice != null && choice.field() != null && choice.source() != null) {
                    sourceByField.put(choice.field(), choice.source().toLowerCase(Locale.ROOT));
                }
            }
        }
        // Default: keep person values where no explicit choice; registration fills blanks
        String firstSource = sourceByField.getOrDefault("firstName", "person");
        String lastSource = sourceByField.getOrDefault("lastName", "person");
        String emailSource = sourceByField.getOrDefault("email", "person");
        String birthSource = sourceByField.getOrDefault("birthdate", "person");

        String first = "registration".equals(firstSource) ? user.getFirstName() : person.getFirstName();
        String last = "registration".equals(lastSource) ? user.getLastName() : person.getLastName();
        String email = "registration".equals(emailSource) ? user.getLoginEmail() : person.getEmail();
        LocalDate birth = "registration".equals(birthSource) ? user.getBirthdate() : person.getBirthdate();

        if (first == null || first.isBlank()) {
            first = user.getFirstName();
        }
        if (last == null || last.isBlank()) {
            last = user.getLastName();
        }
        if (email == null || email.isBlank()) {
            email = user.getLoginEmail();
        }
        if (birth == null) {
            birth = user.getBirthdate();
        }

        person.setFirstName(first.trim());
        person.setLastName(last.trim());
        person.setEmail(email != null ? email.trim().toLowerCase(Locale.ROOT) : null);
        person.setBirthdate(birth);

        user.setFirstName(person.getFirstName());
        user.setLastName(person.getLastName());
        user.setBirthdate(person.getBirthdate());
        if (person.getEmail() != null && !person.getEmail().isBlank()) {
            user.setLoginEmail(person.getEmail());
        }
    }

    private Person createPersonFromRegistration(User user) {
        Person person = new Person();
        person.setUnit(user.getUnit());
        person.setFirstName(user.getFirstName());
        person.setLastName(user.getLastName());
        person.setEmail(user.getLoginEmail());
        person.setBirthdate(user.getBirthdate());
        person.setStatus(PersonStatus.ACTIVE);
        person.setTestData(testModeService.isEnabled());
        return personRepository.save(person);
    }

    private static void addDiff(
            List<FieldDiff> diffs, String field, String label, String registrationValue, String personValue) {
        String reg = normalizeComparable(registrationValue);
        String per = normalizeComparable(personValue);
        if (Objects.equals(reg, per)) {
            return;
        }
        if (reg.isEmpty() && per.isEmpty()) {
            return;
        }
        diffs.add(new FieldDiff(
                field,
                label,
                reg.isEmpty() ? "—" : registrationValue,
                per.isEmpty() ? "—" : personValue));
    }

    private static String normalizeComparable(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.GERMAN);
    }

    private static boolean emailEquals(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private static String formatDate(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static String requireName(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " fehlt.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException(label + " ist zu lang.");
        }
        return trimmed;
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        if (!trimmed.contains("@") || trimmed.length() > 255) {
            return null;
        }
        return trimmed;
    }
}
