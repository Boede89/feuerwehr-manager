package de.feuerwehr.manager.personal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyAreaService {

    private final PersonRepository personRepository;
    private final PersonEmergencyContactRepository emergencyContactRepository;
    private final PersonCourseCompletionRepository completionRepository;
    private final UserRepository userRepository;
    private final TestModeService testModeService;

    private final ObjectMapper exportMapper =
            new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Transactional(readOnly = true)
    public MyAreaView loadView(long userId, Long unitId) {
        User user =
                userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden."));
        Person person = resolveLinkedPerson(userId, unitId).orElse(null);
        List<PersonEmergencyContact> emergencyContacts =
                person != null ? emergencyContactRepository.findByPersonIdOrderBySortOrderAscNameAsc(person.getId()) : List.of();
        List<PersonCourseCompletion> completions =
                person != null ? completionRepository.findByPersonId(person.getId()) : List.of();
        return new MyAreaView(user, person, emergencyContacts, completions);
    }

    @Transactional
    public void updateContact(long userId, Long unitId, String phone, String loginEmail, String address) {
        String normalized = normalizeLoginEmail(loginEmail);
        updateLoginEmail(userId, loginEmail);
        Person person = resolveLinkedPerson(userId, unitId).orElse(null);
        if (person != null) {
            person.setPhone(blankToNull(phone));
            person.setEmail(normalized);
            person.setEmailPrivate(null);
            person.setAddress(blankToNull(address));
            personRepository.save(person);
        }
    }

    @Transactional(readOnly = true)
    public String resolveContactEmail(long userId, Long unitId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden."));
        if (user.getLoginEmail() != null && !user.getLoginEmail().isBlank()) {
            return user.getLoginEmail();
        }
        return resolveLinkedPerson(userId, unitId)
                .map(p -> {
                    if (p.getEmail() != null && !p.getEmail().isBlank()) {
                        return p.getEmail();
                    }
                    return p.getEmailPrivate();
                })
                .orElse(null);
    }

    @Transactional
    public PersonEmergencyContact createEmergencyContact(
            long userId, Long unitId, String name, String phone, String relationship) {
        Person person = requireLinkedPerson(userId, unitId);
        validateEmergencyContact(name, phone);
        PersonEmergencyContact contact = new PersonEmergencyContact();
        contact.setPerson(person);
        contact.setName(name.trim());
        contact.setPhone(phone.trim());
        contact.setRelationship(blankToNull(relationship));
        contact.setSortOrder(emergencyContactRepository.findByPersonIdOrderBySortOrderAscNameAsc(person.getId()).size());
        return emergencyContactRepository.save(contact);
    }

    @Transactional
    public void updateEmergencyContact(
            long userId, Long unitId, long contactId, String name, String phone, String relationship) {
        Person person = requireLinkedPerson(userId, unitId);
        validateEmergencyContact(name, phone);
        PersonEmergencyContact contact = emergencyContactRepository
                .findByIdAndPersonId(contactId, person.getId())
                .orElseThrow(() -> new IllegalArgumentException("Notfallkontakt nicht gefunden."));
        contact.setName(name.trim());
        contact.setPhone(phone.trim());
        contact.setRelationship(blankToNull(relationship));
        emergencyContactRepository.save(contact);
    }

    @Transactional
    public void deleteEmergencyContact(long userId, Long unitId, long contactId) {
        Person person = requireLinkedPerson(userId, unitId);
        PersonEmergencyContact contact = emergencyContactRepository
                .findByIdAndPersonId(contactId, person.getId())
                .orElseThrow(() -> new IllegalArgumentException("Notfallkontakt nicht gefunden."));
        emergencyContactRepository.delete(contact);
    }

    @Transactional(readOnly = true)
    public byte[] exportUserData(long userId, Long unitId) {
        MyAreaView view = loadView(userId, unitId);
        try {
            return exportMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(buildExportMap(view));
        } catch (Exception e) {
            throw new IllegalStateException("Datenexport fehlgeschlagen.", e);
        }
    }

    private Map<String, Object> buildExportMap(MyAreaView view) {
        User user = view.user();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("export_date", Instant.now().toString());

        Map<String, Object> account = new LinkedHashMap<>();
        account.put("username", user.getUsername());
        account.put("display_name", user.getDisplayName());
        account.put("login_email", user.getLoginEmail());
        account.put("role", user.getRole().name());
        account.put("created_at", user.getCreatedAt());
        account.put("last_login_at", user.getLastLoginAt());
        root.put("account", account);

        Person person = view.person();
        if (person != null) {
            Map<String, Object> personMap = new LinkedHashMap<>();
            personMap.put("first_name", person.getFirstName());
            personMap.put("last_name", person.getLastName());
            personMap.put("email", person.getEmail());
            personMap.put("phone", person.getPhone());
            personMap.put("address", person.getAddress());
            personMap.put("birthdate", person.getBirthdate());
            personMap.put("status", person.getStatus().name());
            personMap.put("notes", person.getNotes());
            if (person.getQualificationType() != null) {
                personMap.put("qualification", person.getQualificationType().getName());
            }
            root.put("person", personMap);
        }

        List<Map<String, Object>> contacts = new ArrayList<>();
        for (PersonEmergencyContact c : view.emergencyContacts()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", c.getName());
            row.put("phone", c.getPhone());
            row.put("relationship", c.getRelationship());
            contacts.add(row);
        }
        root.put("emergency_contacts", contacts);

        List<Map<String, Object>> courses = new ArrayList<>();
        for (PersonCourseCompletion cc : view.completions()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("course", cc.getCourse().getName());
            if (cc.getCourse().getQualificationType() != null) {
                row.put("qualification", cc.getCourse().getQualificationType().getName());
            }
            row.put("completion_year", cc.getCompletionYear());
            row.put("completed_on", cc.getCompletedOn());
            courses.add(row);
        }
        root.put("course_completions", courses);

        return root;
    }

    private void updateLoginEmail(long userId, String loginEmail) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden."));
        String normalized = normalizeLoginEmail(loginEmail);
        if (normalized != null
                && userRepository.findByLoginEmailIgnoreCaseExcludingId(normalized, userId).isPresent()) {
            throw new IllegalArgumentException("Diese E-Mail-Adresse wird bereits für die Anmeldung verwendet.");
        }
        user.setLoginEmail(normalized);
        userRepository.save(user);
    }

    private Person requireLinkedPerson(long userId, Long unitId) {
        return resolveLinkedPerson(userId, unitId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ihrem Benutzerkonto ist keine Person zugeordnet. Bitte wenden Sie sich an die Verwaltung."));
    }

    private java.util.Optional<Person> resolveLinkedPerson(long userId, Long unitId) {
        if (unitId == null) {
            return java.util.Optional.empty();
        }
        return personRepository.findActiveByUserIdAndUnitId(userId, unitId, testModeService.isEnabled());
    }

    private static void validateEmergencyContact(String name, String phone) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bitte einen Namen für den Notfallkontakt eingeben.");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Bitte eine Telefonnummer für den Notfallkontakt eingeben.");
        }
    }

    private static String normalizeLoginEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record MyAreaView(
            User user,
            Person person,
            List<PersonEmergencyContact> emergencyContacts,
            List<PersonCourseCompletion> completions) {}
}
