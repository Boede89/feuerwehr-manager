package de.feuerwehr.manager.termine;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonGroup;
import de.feuerwehr.manager.personal.PersonGroupRepository;
import de.feuerwehr.manager.personal.PersonalInstructorGroupService;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TermineService {

    private final UnitTerminRepository unitTerminRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final PersonalService personalService;
    private final PersonGroupRepository personGroupRepository;
    private final PersonalInstructorGroupService personalInstructorGroupService;
    private final TestModeService testModeService;

    @Transactional(readOnly = true)
    public List<DienstplanTerminView> listDienstplanTermine(long unitId) {
        List<UnitTermin> termins =
                unitTerminRepository.findByUnitAndCategoryWithInstructor(unitId, TermineCategory.DIENSTPLAN);
        termins.forEach(this::touchTerminCollections);
        return termins.stream().map(this::toDienstplanView).toList();
    }

    @Transactional(readOnly = true)
    public List<String> listKnownDienstplanThemen(long unitId) {
        LinkedHashSet<String> themen = new LinkedHashSet<>();
        unitTerminRepository
                .findDistinctTitlesByUnitAndCategory(unitId, TermineCategory.DIENSTPLAN)
                .forEach(themen::add);
        personalInstructorGroupService.listThemen(unitId).forEach(themen::add);
        return themen.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    @Transactional
    public void createDienstplanTermin(long unitId, long userId, CreateDienstplanTerminRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Bitte alle Pflichtfelder ausfüllen.");
        }
        if (request.terminDatum() == null) {
            throw new IllegalArgumentException("Bitte ein Datum angeben.");
        }
        if (request.dienstBeginn() == null) {
            throw new IllegalArgumentException("Bitte die Uhrzeit Dienstbeginn angeben.");
        }
        if (request.dienstEnde() == null) {
            throw new IllegalArgumentException("Bitte die Uhrzeit Dienstende angeben.");
        }
        String thema = trimToNull(request.thema());
        if (thema == null) {
            throw new IllegalArgumentException("Bitte ein Thema angeben.");
        }
        LocalDateTime startAt = LocalDateTime.of(request.terminDatum(), request.dienstBeginn());
        LocalDateTime endAt = LocalDateTime.of(request.terminDatum(), request.dienstEnde());
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Dienstende muss nach Dienstbeginn liegen.");
        }

        Unit unit = unitRepository
                .findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        User createdBy = userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden."));
        UnitTermin termin = new UnitTermin();
        termin.setUnit(unit);
        termin.setCategory(TermineCategory.DIENSTPLAN);
        termin.setTitle(thema);
        termin.setStartAt(startAt);
        termin.setEndAt(endAt);
        termin.setInstructorPersons(resolveAssignedPersons(unitId, request.instructorPersonIds()));
        termin.setCreatedBy(createdBy);
        applyAudience(unitId, termin, request);
        unitTerminRepository.save(termin);
    }

    private void applyAudience(long unitId, UnitTermin termin, CreateDienstplanTerminRequest request) {
        if (request.appliesToAll()) {
            termin.setAudienceAll(true);
            termin.getAssignedPersons().clear();
            termin.getAssignedGroups().clear();
            return;
        }
        Set<Person> persons = resolveAssignedPersons(unitId, request.personIds());
        Set<PersonGroup> groups = resolveAssignedGroups(unitId, request.groupIds());
        if (persons.isEmpty() && groups.isEmpty()) {
            throw new IllegalArgumentException(
                    "Bitte mindestens eine Person oder Gruppe auswählen, oder „Alle“ aktiv lassen.");
        }
        termin.setAudienceAll(false);
        termin.setAssignedPersons(persons);
        termin.setAssignedGroups(groups);
    }

    private Set<Person> resolveAssignedPersons(long unitId, List<Long> personIds) {
        Set<Person> persons = new LinkedHashSet<>();
        if (personIds == null) {
            return persons;
        }
        for (Long personId : personIds) {
            if (personId == null || personId <= 0 || persons.stream().anyMatch(p -> p.getId().equals(personId))) {
                continue;
            }
            persons.add(requireUnitPerson(unitId, personId));
        }
        return persons;
    }

    private Set<PersonGroup> resolveAssignedGroups(long unitId, List<Long> groupIds) {
        Set<PersonGroup> groups = new LinkedHashSet<>();
        if (groupIds == null) {
            return groups;
        }
        for (Long groupId : groupIds) {
            if (groupId == null || groupId <= 0 || groups.stream().anyMatch(g -> g.getId().equals(groupId))) {
                continue;
            }
            groups.add(requireUnitGroup(unitId, groupId));
        }
        return groups;
    }

    private PersonGroup requireUnitGroup(long unitId, long groupId) {
        PersonGroup group;
        if (testModeService.isEnabled()) {
            group = personGroupRepository
                    .findByIdWithMembers(groupId, true)
                    .or(() -> personGroupRepository.findByIdWithMembers(groupId, false))
                    .orElseThrow(() -> new IllegalArgumentException("Gruppe nicht gefunden."));
        } else {
            group = personGroupRepository
                    .findByIdWithMembers(groupId, false)
                    .orElseThrow(() -> new IllegalArgumentException("Gruppe nicht gefunden."));
        }
        if (group.getUnit() == null || group.getUnit().getId() != unitId) {
            throw new IllegalArgumentException("Gruppe gehört nicht zu dieser Einheit.");
        }
        return group;
    }

    private Person requireUnitPerson(long unitId, long personId) {
        Person person = personalService.requirePerson(personId);
        if (person.getUnit() == null || person.getUnit().getId() != unitId) {
            throw new IllegalArgumentException("Person gehört nicht zu dieser Einheit.");
        }
        return person;
    }

    private void touchTerminCollections(UnitTermin termin) {
        termin.getInstructorPersons().size();
        if (!termin.isAudienceAll()) {
            termin.getAssignedPersons().size();
            termin.getAssignedGroups().size();
        }
    }

    private DienstplanTerminView toDienstplanView(UnitTermin termin) {
        String ausbilderName = formatInstructorLabel(termin);
        return new DienstplanTerminView(
                termin.getId(),
                termin.getStartAt().toLocalDate(),
                termin.getTitle(),
                termin.getStartAt().toLocalTime(),
                termin.getEndAt() != null ? termin.getEndAt().toLocalTime() : null,
                ausbilderName,
                formatAudienceLabel(termin));
    }

    private String formatInstructorLabel(UnitTermin termin) {
        List<String> names = termin.getInstructorPersons().stream()
                .sorted(Comparator.comparing(Person::anwesenheitDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(Person::anwesenheitDisplayName)
                .toList();
        if (names.isEmpty()) {
            return null;
        }
        return String.join(", ", names);
    }

    private String formatAudienceLabel(UnitTermin termin) {
        if (termin.isAudienceAll()) {
            return "Alle";
        }
        List<String> parts = new ArrayList<>();
        termin.getAssignedGroups().stream()
                .sorted(Comparator.comparing(PersonGroup::getName, String.CASE_INSENSITIVE_ORDER))
                .map(group -> "Gruppe: " + group.getName())
                .forEach(parts::add);
        termin.getAssignedPersons().stream()
                .sorted(Comparator.comparing(Person::anwesenheitDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(Person::anwesenheitDisplayName)
                .forEach(parts::add);
        if (parts.isEmpty()) {
            return "Alle";
        }
        return parts.stream().collect(Collectors.joining(", "));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
