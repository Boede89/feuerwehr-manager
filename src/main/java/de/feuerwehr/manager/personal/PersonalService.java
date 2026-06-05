package de.feuerwehr.manager.personal;

import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.unit.UnitRole;
import de.feuerwehr.manager.unit.UnitRoleService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserManagementService;
import de.feuerwehr.manager.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
public class PersonalService {

    private final UnitRepository unitRepository;
    private final PersonRepository personRepository;
    private final QualificationTypeRepository qualificationTypeRepository;
    private final CourseRepository courseRepository;
    private final PersonCourseCompletionRepository completionRepository;
    private final PersonDiveraRicRepository diveraRicRepository;
    private final UserRepository userRepository;
    private final UserManagementService userManagementService;
    private final UnitRoleService unitRoleService;
    private final TestModeService testModeService;

    private static final SecureRandom LOGIN_PASSWORD_RANDOM = new SecureRandom();

    public Unit requireUnit(long unitId) {
        return unitRepository
                .findVisibleById(unitId, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden"));
    }

    @Transactional(readOnly = true)
    public List<Person> listPersons(long unitId) {
        if (!testModeService.isEnabled()) {
            return personRepository.findActiveByUnitId(unitId, false);
        }
        return mergeByProductionSource(
                personRepository.findActiveByUnitId(unitId, false),
                personRepository.findActiveByUnitId(unitId, true),
                Person::getProductionSourceId,
                Person::getId,
                Comparator.comparing(Person::getLastName).thenComparing(Person::getFirstName));
    }

    public Person requirePerson(long personId) {
        if (!testModeService.isEnabled()) {
            return personRepository
                    .findActiveById(personId, false)
                    .orElseThrow(() -> new IllegalArgumentException("Person nicht gefunden"));
        }
        Optional<Person> testRow = personRepository.findActiveById(personId, true);
        if (testRow.isPresent()) {
            return testRow.get();
        }
        Person prod = personRepository
                .findActiveById(personId, false)
                .orElseThrow(() -> new IllegalArgumentException("Person nicht gefunden"));
        return personRepository.findShadowByProductionSourceId(prod.getId()).orElse(prod);
    }

    @Transactional(readOnly = true)
    public List<QualificationType> listQualificationTypes(long unitId, boolean activeOnly) {
        if (!testModeService.isEnabled()) {
            return activeOnly
                    ? qualificationTypeRepository.findByUnitIdAndTestDataAndActiveTrueOrderBySortOrderAscNameAsc(
                            unitId, false)
                    : qualificationTypeRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, false);
        }
        List<QualificationType> prod = activeOnly
                ? qualificationTypeRepository.findByUnitIdAndTestDataAndActiveTrueOrderBySortOrderAscNameAsc(
                        unitId, false)
                : qualificationTypeRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, false);
        List<QualificationType> test = qualificationTypeRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(
                unitId, true);
        return mergeByProductionSource(
                prod,
                test,
                QualificationType::getProductionSourceId,
                QualificationType::getId,
                Comparator.comparing(QualificationType::getSortOrder).thenComparing(QualificationType::getName));
    }

    @Transactional(readOnly = true)
    public List<Course> listCourses(long unitId, boolean activeOnly) {
        if (!testModeService.isEnabled()) {
            return activeOnly
                    ? courseRepository.findActiveByUnitId(unitId, false)
                    : courseRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, false);
        }
        List<Course> prod = activeOnly
                ? courseRepository.findActiveByUnitId(unitId, false)
                : courseRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, false);
        List<Course> test = courseRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, true);
        return mergeByProductionSource(
                prod,
                test,
                Course::getProductionSourceId,
                Course::getId,
                Comparator.comparing(Course::getSortOrder).thenComparing(Course::getName));
    }

    public List<PersonCourseCompletion> listCompletions(long personId) {
        return completionRepository.findByPersonId(requirePerson(personId).getId());
    }

    public List<PersonDiveraRic> listDiveraRics(long personId) {
        return diveraRicRepository.findByPersonIdOrderByRicCodeAsc(requirePerson(personId).getId());
    }

    /** Person inkl. Lehrgänge und RICs in einer Lesetransaktion (open-in-view: false). */
    @Transactional(readOnly = true)
    public PersonDetailView loadPersonDetailView(long personId) {
        Person person = requirePerson(personId);
        long resolvedId = person.getId();
        List<PersonCourseCompletion> completions = completionRepository.findByPersonId(resolvedId);
        List<PersonDiveraRic> diveraRics = diveraRicRepository.findByPersonIdOrderByRicCodeAsc(resolvedId);
        return new PersonDetailView(person, completions, diveraRics);
    }

    @Transactional
    public Person createPerson(
            long unitId,
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate birthdate,
            Long qualificationTypeId,
            Long userId,
            String diveraUcrId,
            String notes) {
        validateName(firstName, lastName);
        Unit unit = requireUnit(unitId);
        Person person = new Person();
        person.setUnit(unit);
        person.setTestData(testModeService.isEnabled());
        person.setProductionSourceId(null);
        applyFields(person, firstName, lastName, email, phone, birthdate, qualificationTypeId, userId, diveraUcrId, notes);
        person.setStatus(PersonStatus.ACTIVE);
        return personRepository.save(person);
    }

    @Transactional
    public PersonCreateResult createPersonComplete(
            long unitId,
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate birthdate,
            boolean allowLogin,
            String notes,
            PersonStatus status,
            Long qualificationTypeId,
            List<CourseCompletionInput> courseInputs,
            String diveraUcrId,
            List<String> ricCodes,
            long actorUserId,
            HttpServletRequest request) {
        Long linkedUserId = null;
        String generatedPassword = null;
        String createdUsername = null;
        if (allowLogin) {
            String password = generateNumericLoginPassword();
            String username = userManagementService.allocateUniqueUsername(firstName, lastName);
            User user = userManagementService.createUserForPerson(
                    username,
                    (firstName + " " + lastName).trim(),
                    password,
                    unitId,
                    email,
                    actorUserId,
                    request);
            linkedUserId = user.getId();
            generatedPassword = password;
            createdUsername = username;
        }
        Person created = createPerson(
                unitId, firstName, lastName, email, phone, birthdate, null, linkedUserId, null, notes);
        if (status != null) {
            created.setStatus(status);
            personRepository.save(created);
        }
        updateLehrgaenge(created.getId(), qualificationTypeId, courseInputs);
        updateDivera(created.getId(), diveraUcrId, ricCodes);
        return new PersonCreateResult(requirePerson(created.getId()), createdUsername, generatedPassword);
    }

    @Transactional
    public Person updatePerson(
            long personId,
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate birthdate,
            Long qualificationTypeId,
            Long userId,
            String diveraUcrId,
            String notes,
            PersonStatus status) {
        validateName(firstName, lastName);
        Person person = writablePerson(requirePerson(personId));
        applyFields(person, firstName, lastName, email, phone, birthdate, qualificationTypeId, userId, diveraUcrId, notes);
        if (status != null) {
            person.setStatus(status);
        }
        Person saved = personRepository.save(person);
        if (saved.getUser() != null) {
            syncLoginEmailFromPerson(saved);
            syncUserDienstgradFromPersonQualification(saved);
        }
        return saved;
    }

    @Transactional
    public StammdatenUpdateResult updateLoginAccess(
            long personId, boolean allowLogin, long actorUserId, HttpServletRequest request) {
        Person person = writablePerson(requirePerson(personId));
        String generatedPassword = null;
        String createdUsername = null;
        if (allowLogin) {
            if (person.getUser() == null) {
                String password = generateNumericLoginPassword();
                String username =
                        userManagementService.allocateUniqueUsername(person.getFirstName(), person.getLastName());
                User user = userManagementService.createUserForPerson(
                        username,
                        person.displayName(),
                        password,
                        person.getUnit().getId(),
                        person.getEmail(),
                        actorUserId,
                        request);
                person.setUser(user);
                generatedPassword = password;
                createdUsername = username;
            }
        } else {
            person.setUser(null);
        }
        personRepository.save(person);
        Person reloaded = requirePerson(person.getId());
        syncUserDienstgradFromPersonQualification(reloaded);
        return new StammdatenUpdateResult(reloaded, createdUsername, generatedPassword);
    }

    @Transactional
    public StammdatenUpdateResult updateStammdaten(
            long personId,
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate birthdate,
            boolean allowLogin,
            String notes,
            PersonStatus status,
            long actorUserId,
            HttpServletRequest request) {
        validateName(firstName, lastName);
        Person person = writablePerson(requirePerson(personId));
        person.setFirstName(firstName.trim());
        person.setLastName(lastName.trim());
        person.setEmail(blankToNull(email));
        person.setPhone(blankToNull(phone));
        person.setBirthdate(birthdate);
        person.setNotes(blankToNull(notes));
        String generatedPassword = null;
        String createdUsername = null;
        if (allowLogin) {
            if (person.getUser() == null) {
                String password = generateNumericLoginPassword();
                String username = userManagementService.allocateUniqueUsername(firstName, lastName);
                User user = userManagementService.createUserForPerson(
                        username,
                        (firstName + " " + lastName).trim(),
                        password,
                        person.getUnit().getId(),
                        email,
                        actorUserId,
                        request);
                person.setUser(user);
                generatedPassword = password;
                createdUsername = username;
            } else {
                syncLoginEmailFromPerson(person);
            }
        } else {
            person.setUser(null);
        }
        if (status != null) {
            person.setStatus(status);
        }
        Person saved = personRepository.save(person);
        Person reloaded = requirePerson(saved.getId());
        syncUserDienstgradFromPersonQualification(reloaded);
        return new StammdatenUpdateResult(reloaded, createdUsername, generatedPassword);
    }

    private void syncLoginEmailFromPerson(Person person) {
        if (person.getUser() == null) {
            return;
        }
        String email = person.getEmail();
        User user = person.getUser();
        user.setLoginEmail(email == null || email.isBlank() ? null : email.trim().toLowerCase());
        userRepository.save(user);
    }

    @Transactional
    public Person updateLehrgaenge(long personId, Long qualificationTypeId, List<CourseCompletionInput> inputs) {
        Person person = writablePerson(requirePerson(personId));
        if (qualificationTypeId != null && qualificationTypeId > 0) {
            person.setQualificationType(resolveQualificationForWrite(qualificationTypeId, person.getUnit()));
        } else {
            person.setQualificationType(null);
        }
        personRepository.save(person);
        saveCourseCompletions(person.getId(), inputs);
        Person saved = requirePerson(personId);
        syncUserDienstgradFromPersonQualification(saved);
        return saved;
    }

    @Transactional
    public Person updateQualificationType(long personId, Long qualificationTypeId) {
        Person person = writablePerson(requirePerson(personId));
        if (qualificationTypeId != null && qualificationTypeId > 0) {
            person.setQualificationType(resolveQualificationForWrite(qualificationTypeId, person.getUnit()));
        } else {
            person.setQualificationType(null);
        }
        personRepository.save(person);
        Person saved = requirePerson(personId);
        syncUserDienstgradFromPersonQualification(saved);
        return saved;
    }

    @Transactional
    public void addCourseCompletion(long personId, long courseId, Integer completionYear) {
        validateCompletionYear(completionYear);
        Person person = writablePerson(requirePerson(personId));
        long writableId = person.getId();
        if (completionRepository.existsByPersonIdAndCourseId(writableId, courseId)) {
            throw new IllegalArgumentException("Dieser Lehrgang ist bereits hinterlegt.");
        }
        PersonCourseCompletion completion = new PersonCourseCompletion();
        completion.setPerson(person);
        completion.setCourse(resolveCourseForWrite(courseId, person.getUnit()));
        completion.setCompletionYear(completionYear);
        completionRepository.save(completion);
    }

    @Transactional
    public void updateCourseCompletion(long personId, long completionId, long courseId, Integer completionYear) {
        validateCompletionYear(completionYear);
        Person person = writablePerson(requirePerson(personId));
        long writableId = person.getId();
        PersonCourseCompletion completion = completionRepository
                .findById(completionId)
                .filter(row -> row.getPerson().getId().equals(writableId))
                .orElseThrow(() -> new IllegalArgumentException("Lehrgangseintrag nicht gefunden."));
        if (!completion.getCourse().getId().equals(courseId)
                && completionRepository.existsByPersonIdAndCourseId(writableId, courseId)) {
            throw new IllegalArgumentException("Dieser Lehrgang ist bereits hinterlegt.");
        }
        completion.setCourse(resolveCourseForWrite(courseId, person.getUnit()));
        completion.setCompletionYear(completionYear);
        completionRepository.save(completion);
    }

    @Transactional
    public void deleteCourseCompletion(long personId, long completionId) {
        writablePerson(requirePerson(personId));
        PersonCourseCompletion completion = completionRepository
                .findById(completionId)
                .filter(row -> row.getPerson().getId().equals(personId))
                .orElseThrow(() -> new IllegalArgumentException("Lehrgangseintrag nicht gefunden."));
        completionRepository.delete(completion);
    }

    @Transactional
    public Person updateDivera(long personId, String diveraUcrId, List<String> ricCodes) {
        Person person = writablePerson(requirePerson(personId));
        person.setDiveraUcrId(blankToNull(diveraUcrId));
        personRepository.save(person);
        long writableId = person.getId();
        diveraRicRepository.deleteByPersonId(writableId);
        if (ricCodes != null) {
            for (String raw : ricCodes) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                PersonDiveraRic ric = new PersonDiveraRic();
                ric.setPerson(person);
                ric.setRicCode(raw.trim());
                diveraRicRepository.save(ric);
            }
        }
        return requirePerson(personId);
    }

    @Transactional
    public void saveCourseCompletions(long personId, List<CourseCompletionInput> inputs) {
        Person person = writablePerson(requirePerson(personId));
        long writableId = person.getId();
        completionRepository.deleteByPersonId(writableId);
        if (inputs == null || inputs.isEmpty()) {
            return;
        }
        for (CourseCompletionInput input : inputs) {
            if (input.courseId() == null || input.courseId() <= 0) {
                continue;
            }
            Course course = resolveCourseForWrite(input.courseId(), person.getUnit());
            PersonCourseCompletion completion = new PersonCourseCompletion();
            completion.setPerson(person);
            completion.setCourse(course);
            completion.setCompletionYear(input.completionYear());
            completion.setCompletedOn(input.completedOn());
            completionRepository.save(completion);
        }
    }

    @Transactional
    public QualificationType createQualificationType(long unitId, String name, Long dienstgradRoleId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name fehlt");
        }
        Unit unit = requireUnit(unitId);
        QualificationType type = new QualificationType();
        type.setUnit(unit);
        type.setName(name.trim());
        applyDienstgradRoleOnQualification(type, unit, dienstgradRoleId);
        type.setSortOrder((int) qualificationTypeRepository
                .findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, testModeService.isEnabled())
                .size());
        type.setActive(true);
        type.setTestData(testModeService.isEnabled());
        type.setProductionSourceId(null);
        return qualificationTypeRepository.save(type);
    }

    @Transactional
    public Course createCourse(long unitId, String name, Long qualificationTypeId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name fehlt");
        }
        Unit unit = requireUnit(unitId);
        Course course = new Course();
        course.setUnit(unit);
        course.setName(name.trim());
        if (qualificationTypeId != null && qualificationTypeId > 0) {
            course.setQualificationType(resolveQualificationForWrite(qualificationTypeId, unit));
        }
        course.setSortOrder((int) courseRepository
                .findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, testModeService.isEnabled())
                .size());
        course.setActive(true);
        course.setTestData(testModeService.isEnabled());
        course.setProductionSourceId(null);
        return courseRepository.save(course);
    }

    @Transactional
    public void moveQualificationType(long unitId, long qualificationTypeId, String direction) {
        Unit unit = requireUnit(unitId);
        List<QualificationType> items = new ArrayList<>(listQualificationTypes(unitId, false));
        reorderList(items, qualificationTypeId, direction);
        persistQualificationSortOrder(unit, items);
    }

    @Transactional
    public void moveCourse(long unitId, long courseId, String direction) {
        Unit unit = requireUnit(unitId);
        List<Course> items = new ArrayList<>(listCourses(unitId, false));
        reorderList(items, courseId, direction);
        persistCourseSortOrder(unit, items);
    }

    @Transactional
    public QualificationType updateQualificationType(
            long unitId, long qualificationTypeId, String name, boolean active, Long dienstgradRoleId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name fehlt");
        }
        Unit unit = requireUnit(unitId);
        QualificationType type = resolveQualificationForWrite(qualificationTypeId, unit);
        type.setName(name.trim());
        type.setActive(active);
        applyDienstgradRoleOnQualification(type, unit, dienstgradRoleId);
        QualificationType saved = qualificationTypeRepository.save(type);
        syncUsersDienstgradForQualification(saved);
        return saved;
    }

    @Transactional
    public void deleteQualificationType(long unitId, long qualificationTypeId) {
        Unit unit = requireUnit(unitId);
        QualificationType type = resolveQualificationForWrite(qualificationTypeId, unit);
        qualificationTypeRepository.delete(type);
    }

    @Transactional
    public Course updateCourse(
            long unitId, long courseId, String name, Long qualificationTypeId, boolean active) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name fehlt");
        }
        Unit unit = requireUnit(unitId);
        Course course = resolveCourseForWrite(courseId, unit);
        course.setName(name.trim());
        course.setActive(active);
        if (qualificationTypeId != null && qualificationTypeId > 0) {
            course.setQualificationType(resolveQualificationForWrite(qualificationTypeId, unit));
        } else {
            course.setQualificationType(null);
        }
        return courseRepository.save(course);
    }

    @Transactional
    public void deleteCourse(long unitId, long courseId) {
        Unit unit = requireUnit(unitId);
        Course course = resolveCourseForWrite(courseId, unit);
        courseRepository.delete(course);
    }

    @Transactional
    public void anonymizePerson(long personId) {
        Person person = writablePerson(requirePerson(personId));
        person.setFirstName("Gelöscht");
        person.setLastName("#" + person.getId());
        person.setEmail(null);
        person.setPhone(null);
        person.setBirthdate(null);
        person.setNotes(null);
        person.setDiveraUcrId(null);
        person.setUser(null);
        person.setQualificationType(null);
        person.setStatus(PersonStatus.INACTIVE);
        person.setAnonymizedAt(Instant.now());
        completionRepository.deleteByPersonId(person.getId());
        diveraRicRepository.deleteByPersonId(person.getId());
        personRepository.save(person);
    }

    private void applyFields(
            Person person,
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate birthdate,
            Long qualificationTypeId,
            Long userId,
            String diveraUcrId,
            String notes) {
        person.setFirstName(firstName.trim());
        person.setLastName(lastName.trim());
        person.setEmail(blankToNull(email));
        person.setPhone(blankToNull(phone));
        person.setBirthdate(birthdate);
        person.setDiveraUcrId(blankToNull(diveraUcrId));
        person.setNotes(blankToNull(notes));
        if (qualificationTypeId != null && qualificationTypeId > 0) {
            person.setQualificationType(resolveQualificationForWrite(qualificationTypeId, person.getUnit()));
        } else {
            person.setQualificationType(null);
        }
        if (userId != null && userId > 0) {
            User user = userRepository.findById(userId).orElseThrow();
            if (user.getAnonymizedAt() != null) {
                throw new IllegalArgumentException("Benutzerkonto ist gelöscht");
            }
            person.setUser(user);
        } else {
            person.setUser(null);
        }
    }

    /** Im Testmodus: Produktiv-Datensatz nur lesen, Änderungen auf Schattenkopie. */
    private Person writablePerson(Person viewed) {
        if (!testModeService.isEnabled() || viewed.isTestData()) {
            return viewed;
        }
        return personRepository
                .findShadowByProductionSourceId(viewed.getId())
                .orElseGet(() -> {
                    Person shadow = personRepository.save(copyPersonToShadow(viewed));
                    copyRelatedDataToShadow(viewed, shadow);
                    return shadow;
                });
    }

    private Person copyPersonToShadow(Person prod) {
        Person shadow = new Person();
        shadow.setUnit(prod.getUnit());
        shadow.setUser(prod.getUser());
        shadow.setFirstName(prod.getFirstName());
        shadow.setLastName(prod.getLastName());
        shadow.setEmail(prod.getEmail());
        shadow.setPhone(prod.getPhone());
        shadow.setBirthdate(prod.getBirthdate());
        shadow.setQualificationType(prod.getQualificationType());
        shadow.setStatus(prod.getStatus());
        shadow.setDiveraUcrId(prod.getDiveraUcrId());
        shadow.setNotes(prod.getNotes());
        shadow.setTestData(true);
        shadow.setProductionSourceId(prod.getId());
        return shadow;
    }

    private void copyRelatedDataToShadow(Person prod, Person shadow) {
        for (PersonCourseCompletion completion : completionRepository.findByPersonId(prod.getId())) {
            PersonCourseCompletion copy = new PersonCourseCompletion();
            copy.setPerson(shadow);
            copy.setCourse(resolveCourseForWrite(completion.getCourse().getId(), shadow.getUnit()));
            copy.setCompletionYear(completion.getCompletionYear());
            copy.setCompletedOn(completion.getCompletedOn());
            completionRepository.save(copy);
        }
        for (PersonDiveraRic ric : diveraRicRepository.findByPersonIdOrderByRicCodeAsc(prod.getId())) {
            PersonDiveraRic copy = new PersonDiveraRic();
            copy.setPerson(shadow);
            copy.setRicCode(ric.getRicCode());
            diveraRicRepository.save(copy);
        }
    }

    private static String generateNumericLoginPassword() {
        return String.format("%04d", LOGIN_PASSWORD_RANDOM.nextInt(10_000));
    }

    private QualificationType resolveQualificationForWrite(long qualificationTypeId, Unit unit) {
        QualificationType qt = qualificationTypeRepository
                .findById(qualificationTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Qualifikation nicht gefunden"));
        if (!testModeService.isEnabled()) {
            if (qt.isTestData()) {
                throw new IllegalArgumentException("Qualifikation nicht im Produktivmodus verfügbar");
            }
            return qt;
        }
        if (qt.isTestData()) {
            return qt;
        }
        if (!qt.getUnit().getId().equals(unit.getId())) {
            throw new IllegalArgumentException("Qualifikation gehört nicht zur Einheit");
        }
        return qualificationTypeRepository
                .findShadowByProductionSourceId(qt.getId())
                .orElseGet(() -> qualificationTypeRepository.save(copyQualificationToShadow(qt)));
    }

    private QualificationType copyQualificationToShadow(QualificationType prod) {
        QualificationType shadow = new QualificationType();
        shadow.setUnit(prod.getUnit());
        shadow.setName(prod.getName());
        shadow.setSortOrder(prod.getSortOrder());
        shadow.setActive(prod.isActive());
        shadow.setTestData(true);
        shadow.setProductionSourceId(prod.getId());
        shadow.setDienstgradRole(prod.getDienstgradRole());
        return shadow;
    }

    private void applyDienstgradRoleOnQualification(
            QualificationType type, Unit unit, Long dienstgradRoleId) {
        if (dienstgradRoleId == null || dienstgradRoleId <= 0) {
            type.setDienstgradRole(null);
            return;
        }
        UnitRole role = unitRoleService.requireDienstgradRole(unit.getId(), dienstgradRoleId);
        type.setDienstgradRole(role);
    }

    private void syncUserDienstgradFromPersonQualification(Person person) {
        if (person.getUser() == null) {
            return;
        }
        Long roleId = null;
        QualificationType qt = person.getQualificationType();
        if (qt != null && qt.getDienstgradRole() != null) {
            roleId = qt.getDienstgradRole().getId();
        } else if (qt != null) {
            roleId = qualificationTypeRepository
                    .findByIdWithDienstgradRole(qt.getId())
                    .map(q -> q.getDienstgradRole())
                    .filter(r -> r != null)
                    .map(UnitRole::getId)
                    .orElse(null);
        }
        userManagementService.syncDienstgradForUser(person.getUser().getId(), roleId);
    }

    private void syncUsersDienstgradForQualification(QualificationType qualification) {
        QualificationType loaded = qualificationTypeRepository
                .findByIdWithDienstgradRole(qualification.getId())
                .orElse(qualification);
        Long roleId =
                loaded.getDienstgradRole() != null ? loaded.getDienstgradRole().getId() : null;
        for (Person person : personRepository.findByQualificationTypeId(loaded.getId())) {
            if (person.getUser() != null) {
                userManagementService.syncDienstgradForUser(person.getUser().getId(), roleId);
            }
        }
    }

    private static void reorderList(List<?> items, long itemId, String direction) {
        int idx = indexOfId(items, itemId);
        if (idx < 0) {
            throw new IllegalArgumentException("Eintrag nicht gefunden.");
        }
        int delta = "down".equalsIgnoreCase(direction != null ? direction.trim() : "") ? 1 : -1;
        int newIdx = idx + delta;
        if (newIdx < 0 || newIdx >= items.size()) {
            return;
        }
        Collections.swap(items, idx, newIdx);
    }

    private static int indexOfId(List<?> items, long itemId) {
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            Long id = null;
            if (item instanceof QualificationType q) {
                id = q.getId();
            } else if (item instanceof Course c) {
                id = c.getId();
            }
            if (id != null && id.equals(itemId)) {
                return i;
            }
        }
        return -1;
    }

    private void persistQualificationSortOrder(Unit unit, List<QualificationType> items) {
        for (int i = 0; i < items.size(); i++) {
            QualificationType q = resolveQualificationForWrite(items.get(i).getId(), unit);
            q.setSortOrder(i);
            qualificationTypeRepository.save(q);
        }
    }

    private void persistCourseSortOrder(Unit unit, List<Course> items) {
        for (int i = 0; i < items.size(); i++) {
            Course c = resolveCourseForWrite(items.get(i).getId(), unit);
            c.setSortOrder(i);
            courseRepository.save(c);
        }
    }

    private Course resolveCourseForWrite(long courseId, Unit unit) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new IllegalArgumentException("Lehrgang nicht gefunden"));
        if (!testModeService.isEnabled()) {
            if (course.isTestData()) {
                throw new IllegalArgumentException("Lehrgang nicht im Produktivmodus verfügbar");
            }
            return course;
        }
        if (course.isTestData()) {
            return course;
        }
        if (!course.getUnit().getId().equals(unit.getId())) {
            throw new IllegalArgumentException("Lehrgang gehört nicht zur Einheit");
        }
        return courseRepository
                .findShadowByProductionSourceId(course.getId())
                .orElseGet(() -> courseRepository.save(copyCourseToShadow(course)));
    }

    private Course copyCourseToShadow(Course prod) {
        Course shadow = new Course();
        shadow.setUnit(prod.getUnit());
        shadow.setName(prod.getName());
        shadow.setQualificationType(prod.getQualificationType());
        shadow.setActive(prod.isActive());
        shadow.setTestData(true);
        shadow.setProductionSourceId(prod.getId());
        shadow.setSortOrder(prod.getSortOrder());
        return shadow;
    }

    private static <T> List<T> mergeByProductionSource(
            List<T> production,
            List<T> testRows,
            Function<T, Long> sourceIdExtractor,
            Function<T, Long> idExtractor,
            Comparator<T> comparator) {
        Map<Long, T> shadowsBySource = testRows.stream()
                .filter(row -> sourceIdExtractor.apply(row) != null)
                .collect(Collectors.toMap(sourceIdExtractor, Function.identity(), (a, b) -> a));
        List<T> testOnly =
                testRows.stream().filter(row -> sourceIdExtractor.apply(row) == null).toList();
        List<T> merged = new ArrayList<>();
        for (T prod : production) {
            merged.add(shadowsBySource.getOrDefault(idExtractor.apply(prod), prod));
        }
        merged.addAll(testOnly);
        merged.sort(comparator);
        return merged;
    }

    private static void validateName(String firstName, String lastName) {
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Vor- und Nachname sind Pflicht");
        }
    }

    private static void validateCompletionYear(Integer completionYear) {
        if (completionYear == null) {
            return;
        }
        if (completionYear < 1950 || completionYear > 2100) {
            throw new IllegalArgumentException("Bitte ein gültiges Jahr zwischen 1950 und 2100 angeben.");
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record CourseCompletionInput(Long courseId, Integer completionYear, LocalDate completedOn) {}

    public record PersonCreateResult(Person person, String createdUsername, String initialPassword) {}

    public record StammdatenUpdateResult(Person person, String createdUsername, String initialPassword) {}

    public record PersonDetailView(
            Person person, List<PersonCourseCompletion> completions, List<PersonDiveraRic> diveraRics) {}

    @Transactional
    public void seedDefaultQualificationsIfEmpty(long unitId) {
        boolean testMode = testModeService.isEnabled();
        if (!qualificationTypeRepository
                .findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, testMode)
                .isEmpty()) {
            return;
        }
        if (testMode
                && !qualificationTypeRepository
                        .findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, false)
                        .isEmpty()) {
            return;
        }
        String[] defaults = {"Mannschaft", "Truppführer", "Gruppenführer", "Zugführer"};
        Unit unit = requireUnit(unitId);
        int order = 0;
        for (String name : defaults) {
            QualificationType type = new QualificationType();
            type.setUnit(unit);
            type.setName(name);
            type.setSortOrder(order++);
            type.setActive(true);
            type.setTestData(testMode);
            type.setProductionSourceId(null);
            qualificationTypeRepository.save(type);
        }
    }
}
