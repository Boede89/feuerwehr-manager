package de.feuerwehr.manager.personal;

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
    private final UserRepository userRepository;

    public Unit requireUnit(long unitId) {
        return unitRepository.findById(unitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden"));
    }

    public List<Person> listPersons(long unitId) {
        return personRepository.findActiveByUnitId(unitId);
    }

    @Transactional(readOnly = true)
    public Person requirePerson(long personId) {
        return personRepository.findActiveById(personId).orElseThrow(() -> new IllegalArgumentException("Person nicht gefunden"));
    }

    public List<QualificationType> listQualificationTypes(long unitId, boolean activeOnly) {
        return activeOnly
                ? qualificationTypeRepository.findByUnitIdAndActiveTrueOrderBySortOrderAscNameAsc(unitId)
                : qualificationTypeRepository.findByUnitIdOrderBySortOrderAscNameAsc(unitId);
    }

    public List<Course> listCourses(long unitId, boolean activeOnly) {
        return activeOnly ? courseRepository.findActiveByUnitId(unitId) : courseRepository.findByUnitIdOrderByNameAsc(unitId);
    }

    @Transactional(readOnly = true)
    public List<PersonCourseCompletion> listCompletions(long personId) {
        return completionRepository.findByPersonId(personId);
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
            Course course = courseRepository.findById(input.courseId()).orElseThrow();
            if (!course.getUnit().getId().equals(person.getUnit().getId())) {
                throw new IllegalArgumentException("Lehrgang gehört nicht zur Einheit");
            }
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
        type.setSortOrder((int) qualificationTypeRepository.findByUnitIdOrderBySortOrderAscNameAsc(unitId).size());
        type.setActive(true);
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
            course.setQualificationType(qualificationTypeRepository.findById(qualificationTypeId).orElseThrow());
        }
        course.setActive(true);
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
            QualificationType qt = qualificationTypeRepository.findById(qualificationTypeId).orElseThrow();
            if (!qt.getUnit().getId().equals(person.getUnit().getId())) {
                throw new IllegalArgumentException("Qualifikation gehört nicht zur Einheit");
            }
            person.setQualificationType(qt);
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
        if (!qualificationTypeRepository.findByUnitIdOrderBySortOrderAscNameAsc(unitId).isEmpty()) {
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
            qualificationTypeRepository.save(type);
        }
    }
}
