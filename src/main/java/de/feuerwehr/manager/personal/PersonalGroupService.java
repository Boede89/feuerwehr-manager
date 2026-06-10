package de.feuerwehr.manager.personal;

import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersonalGroupService {

    private final PersonGroupRepository groupRepository;
    private final PersonRepository personRepository;
    private final UnitRepository unitRepository;
    private final TestModeService testModeService;

    @Transactional(readOnly = true)
    public List<PersonGroup> listGroups(long unitId) {
        if (!testModeService.isEnabled()) {
            return groupRepository.findByUnitIdWithMembers(unitId, false);
        }
        List<PersonGroup> merged = new ArrayList<>(groupRepository.findByUnitIdWithMembers(unitId, false));
        merged.addAll(groupRepository.findByUnitIdWithMembers(unitId, true));
        merged.sort(Comparator.comparing(PersonGroup::getName));
        return merged;
    }

    @Transactional
    public PersonGroup createGroup(long unitId, String name, List<Long> personIds) {
        validateName(name);
        Unit unit = requireUnit(unitId);
        boolean testData = testModeService.isEnabled();
        if (groupRepository.existsByUnitIdAndNameIgnoreCaseAndTestData(unitId, name.trim(), testData)) {
            throw new IllegalArgumentException("Eine Gruppe mit diesem Namen existiert bereits.");
        }
        PersonGroup group = new PersonGroup();
        group.setUnit(unit);
        group.setName(name.trim());
        group.setTestData(testData);
        group.setMembers(resolveMembers(unitId, personIds, testData));
        return groupRepository.save(group);
    }

    @Transactional
    public PersonGroup updateGroup(long groupId, String name, List<Long> personIds) {
        validateName(name);
        PersonGroup group = requireWritableGroup(groupId);
        boolean testData = testModeService.isEnabled();
        if (groupRepository.existsByUnitIdAndNameIgnoreCaseAndTestDataAndIdNot(
                group.getUnit().getId(), name.trim(), testData, group.getId())) {
            throw new IllegalArgumentException("Eine Gruppe mit diesem Namen existiert bereits.");
        }
        group.setName(name.trim());
        group.getMembers().clear();
        group.getMembers().addAll(resolveMembers(group.getUnit().getId(), personIds, testData));
        return groupRepository.save(group);
    }

    @Transactional
    public void deleteGroup(long groupId) {
        PersonGroup group = requireWritableGroup(groupId);
        groupRepository.delete(group);
    }

    private PersonGroup requireWritableGroup(long groupId) {
        if (!testModeService.isEnabled()) {
            return groupRepository
                    .findByIdWithMembers(groupId, false)
                    .orElseThrow(() -> new IllegalArgumentException("Gruppe nicht gefunden."));
        }
        return groupRepository
                .findByIdWithMembers(groupId, true)
                .orElseThrow(() -> new IllegalArgumentException("Gruppe nicht gefunden."));
    }

    private Unit requireUnit(long unitId) {
        return unitRepository
                .findVisibleById(unitId, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden"));
    }

    private List<Person> resolveMembers(long unitId, List<Long> personIds, boolean testData) {
        if (personIds == null || personIds.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long personId : personIds) {
            if (personId != null && personId > 0) {
                uniqueIds.add(personId);
            }
        }
        List<Person> members = new ArrayList<>();
        for (Long personId : uniqueIds) {
            Person person = personRepository
                    .findActiveById(personId, testData)
                    .orElseThrow(() -> new IllegalArgumentException("Person nicht gefunden: " + personId));
            if (person.getUnit().getId() != unitId) {
                throw new IllegalArgumentException("Person gehört nicht zu dieser Einheit.");
            }
            members.add(person);
        }
        return members;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bitte einen Gruppennamen angeben.");
        }
        if (name.trim().length() > 100) {
            throw new IllegalArgumentException("Gruppenname darf maximal 100 Zeichen lang sein.");
        }
    }
}
