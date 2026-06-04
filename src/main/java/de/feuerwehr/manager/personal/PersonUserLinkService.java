package de.feuerwehr.manager.personal;

import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Legt bei Bedarf einen Personenstammdatensatz für ein Benutzerkonto in dessen Einheit an. */
@Service
@RequiredArgsConstructor
public class PersonUserLinkService {

    private final PersonRepository personRepository;
    private final UserRepository userRepository;
    private final TestModeService testModeService;

    @Transactional
    public void ensurePersonForUser(User user) {
        if (user == null || user.getUnit() == null || user.getAnonymizedAt() != null) {
            return;
        }
        Unit unit = user.getUnit();
        boolean testData = testModeService.isEnabled();
        if (personRepository
                .findActiveByUserIdAndUnitId(user.getId(), unit.getId(), testData)
                .isPresent()) {
            return;
        }
        String[] names = splitDisplayName(user.getDisplayName());
        Person person = new Person();
        person.setUnit(unit);
        person.setUser(user);
        person.setFirstName(names[0]);
        person.setLastName(names[1]);
        person.setEmail(user.getLoginEmail());
        person.setStatus(PersonStatus.ACTIVE);
        person.setTestData(testData);
        personRepository.save(person);
        syncLoginEmailFromLinkedPersons(user);
    }

    /** Übernimmt E-Mail aus verknüpfter Person, falls login_email noch leer ist. */
    @Transactional
    public void syncLoginEmailFromLinkedPersons(User user) {
        if (user == null || user.getId() == null || user.getLoginEmail() != null) {
            return;
        }
        personRepository.findAllByUserIdAndAnonymizedAtIsNull(user.getId()).stream()
                .map(Person::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .findFirst()
                .ifPresent(email -> {
                    user.setLoginEmail(email.trim().toLowerCase());
                    userRepository.save(user);
                });
    }

    private static String[] splitDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return new String[] {"Benutzer", "—"};
        }
        String trimmed = displayName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new String[] {trimmed, "—"};
        }
        return new String[] {trimmed.substring(0, space).trim(), trimmed.substring(space + 1).trim()};
    }
}
