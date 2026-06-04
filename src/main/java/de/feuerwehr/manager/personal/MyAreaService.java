package de.feuerwehr.manager.personal;

import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.util.List;
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
    public void updateContact(long userId, Long unitId, String phone, String emailPrivate, String address) {
        Person person = requireLinkedPerson(userId, unitId);
        person.setPhone(blankToNull(phone));
        person.setEmailPrivate(blankToNull(emailPrivate));
        person.setAddress(blankToNull(address));
        personRepository.save(person);
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
