package de.feuerwehr.manager.personal;

import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private final TestModeService testModeService;

    private boolean scope() {
        return testModeService.testDataScope();
    }

    public Unit requireUnit(long unitId) {
        return unitRepository
                .findVisibleById(unitId, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden"));
    }

    public List<Person> listPersons(long unitId) {
        return personRepository.findActiveByUnitId(unitId, scope());
    }

    @Transactional(readOnly = true)
    public Person requirePerson(long personId) {
        return personRepository
                .findActiveById(personId, scope())
                .orElseThrow(() -> new IllegalArgumentException("Person nicht gefunden"));
    }

    public List<QualificationType> listQualificationTypes(long unitId, boolean activeOnly) {
        boolean testData = scope();
        return activeOnly
                ? qualificationTypeRepository.findByUnitIdAndTestDataAndActiveTrueOrderBySortOrderAscNameAsc(
                        unitId, testData)
                : qualificationTypeRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, testData);
    }

    public List<Course> listCourses(long unitId, boolean activeOnly) {
        boolean testData = scope();
        return activeOnly
                ? courseRepository.findActiveByUnitId(unitId, testData)
                : courseRepository.findByUnitIdAndTestDataOrderByNameAsc(unitId, testData);
    }

    @Transactional(readOnly = true)
    public List<PersonCourseCompletion> listCompletions(long personId) {
        return completionRepository.findByPersonId(personId, scope());
    }

    @Transactional(readOnly = true)
    public List<PersonDiveraRic> listDiveraRics(long personId) {
        return diveraRicRepository.findByPersonIdOrderByRicCodeAsc(personId);
    }

    public List<User> listLinkableUsers() {
        return userRepository.findAllByAnonymizedAtIsNullOrderByUsernameAsc();
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
        person.setTestData(scope());
        applyFields(person, firstName, lastName, email, phone, birthdate, qualificationTypeId, userId, diveraUcrId, notes);
        person.setStatus(PersonStatus.ACTIVE);
        return personRepository.save(person);
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
        Person person = requirePerson(personId);
        applyFields(person, firstName, lastName, email, phone, birthdate, qualificationTypeId, userId, diveraUcrId, notes);
        if (status != null) {
            person.setStatus(status);
        }
        return personRepository.save(person);
    }

    @Transactional
    public Person updateStammdaten(
            long personId,
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate birthdate,
            Long userId,
            String notes,
            PersonStatus status) {
        validateName(firstName, lastName);
        Person person = requirePerson(personId);
        person.setFirstName(firstName.trim());
        person.setLastName(lastName.trim());
        person.setEmail(blankToNull(email));
        person.setPhone(blankToNull(phone));
        person.setBirthdate(birthdate);
        person.setNotes(blankToNull(notes));
        if (userId != null && userId > 0) {
            User user = userRepository.findById(userId).orElseThrow();
            if (user.getAnonymizedAt() != null) {
                throw new IllegalArgumentException("Benutzerkonto ist gelöscht");
            }
            person.setUser(user);
        } else {
            person.setUser(null);
        }
        if (status != null) {
            person.setStatus(status);
        }
        return personRepository.save(person);
    }

    @Transactional
    public Person updateLehrgaenge(long personId, Long qualificationTypeId, List<CourseCompletionInput> inputs) {
        Person person = requirePerson(personId);
        if (qualificationTypeId != null && qualificationTypeId > 0) {
            person.setQualificationType(requireQualification(qualificationTypeId, person));
        } else {
            person.setQualificationType(null);
        }
        personRepository.save(person);
        saveCourseCompletions(personId, inputs);
        return requirePerson(personId);
    }

    @Transactional
    public Person updateDivera(long personId, String diveraUcrId, List<String> ricCodes) {
        Person person = requirePerson(personId);
        person.setDiveraUcrId(blankToNull(diveraUcrId));
        personRepository.save(person);
        diveraRicRepository.deleteByPersonId(personId);
        if (ricCodes != null) {
            for (String raw : ricCodes) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String code = raw.trim();
                PersonDiveraRic ric = new PersonDiveraRic();
                ric.setPerson(person);
                ric.setRicCode(code);
                diveraRicRepository.save(ric);
            }
        }
        return requirePerson(personId);
    }

    @Transactional
    public void saveCourseCompletions(long personId, List<CourseCompletionInput> inputs) {
        Person person = requirePerson(personId);
        completionRepository.deleteByPersonId(personId);
        if (inputs == null || inputs.isEmpty()) {
            return;
        }
        for (CourseCompletionInput input : inputs) {
            if (input.courseId() == null || input.courseId() <= 0) {
                continue;
            }
            Course course = requireCourse(input.courseId(), person);
            PersonCourseCompletion completion = new PersonCourseCompletion();
            completion.setPerson(person);
            completion.setCourse(course);
            completion.setCompletionYear(input.completionYear());
            completion.setCompletedOn(input.completedOn());
            completionRepository.save(completion);
        }
    }

    @Transactional
    public QualificationType createQualificationType(long unitId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name fehlt");
        }
        Unit unit = requireUnit(unitId);
        QualificationType type = new QualificationType();
        type.setUnit(unit);
        type.setName(name.trim());
        type.setSortOrder((int) qualificationTypeRepository
                .findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, scope())
                .size());
        type.setActive(true);
        type.setTestData(scope());
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
            course.setQualificationType(requireQualification(qualificationTypeId, unit));
        }
        course.setActive(true);
        course.setTestData(scope());
        return courseRepository.save(course);
    }

    @Transactional
    public void anonymizePerson(long personId) {
        Person person = requirePerson(personId);
        person.setFirstName("Gelöscht");
        person.setLastName("#" + personId);
        person.setEmail(null);
        person.setPhone(null);
        person.setBirthdate(null);
        person.setNotes(null);
        person.setDiveraUcrId(null);
        person.setUser(null);
        person.setQualificationType(null);
        person.setStatus(PersonStatus.INACTIVE);
        person.setAnonymizedAt(Instant.now());
        completionRepository.deleteByPersonId(personId);
        diveraRicRepository.deleteByPersonId(personId);
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
            person.setQualificationType(requireQualification(qualificationTypeId, person));
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

    private static void validateName(String firstName, String lastName) {
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Vor- und Nachname sind Pflicht");
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record CourseCompletionInput(Long courseId, Integer completionYear, LocalDate completedOn) {}

    /** Standard-Qualifikationen beim ersten Öffnen anlegen. */
    @Transactional
    public void seedDefaultQualificationsIfEmpty(long unitId) {
        if (!qualificationTypeRepository
                .findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, scope())
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
            type.setTestData(scope());
            qualificationTypeRepository.save(type);
        }
    }

    private QualificationType requireQualification(long qualificationTypeId, Person person) {
        QualificationType qt = qualificationTypeRepository
                .findById(qualificationTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Qualifikation nicht gefunden"));
        if (qt.isTestData() != person.isTestData()) {
            throw new IllegalArgumentException("Qualifikation gehört nicht zum aktuellen Datenbereich");
        }
        if (!qt.getUnit().getId().equals(person.getUnit().getId())) {
            throw new IllegalArgumentException("Qualifikation gehört nicht zur Einheit");
        }
        return qt;
    }

    private QualificationType requireQualification(long qualificationTypeId, Unit unit) {
        QualificationType qt = qualificationTypeRepository
                .findById(qualificationTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Qualifikation nicht gefunden"));
        if (qt.isTestData() != scope()) {
            throw new IllegalArgumentException("Qualifikation gehört nicht zum aktuellen Datenbereich");
        }
        if (!qt.getUnit().getId().equals(unit.getId())) {
            throw new IllegalArgumentException("Qualifikation gehört nicht zur Einheit");
        }
        return qt;
    }

    private Course requireCourse(long courseId, Person person) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new IllegalArgumentException("Lehrgang nicht gefunden"));
        if (course.isTestData() != person.isTestData()) {
            throw new IllegalArgumentException("Lehrgang gehört nicht zum aktuellen Datenbereich");
        }
        if (!course.getUnit().getId().equals(person.getUnit().getId())) {
            throw new IllegalArgumentException("Lehrgang gehört nicht zur Einheit");
        }
        return course;
    }
}
