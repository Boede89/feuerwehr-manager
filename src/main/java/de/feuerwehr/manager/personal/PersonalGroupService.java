package de.feuerwehr.manager.personal;

import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
            members.add(person);
        }
        return members;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listOtherUnits(long unitId) {
        requireUnit(unitId);
        List<Map<String, Object>> units = new ArrayList<>();
        for (Unit unit : unitRepository.findActiveVisible(testModeService.isEnabled())) {
            if (unit.getId() == unitId) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", unit.getId());
            item.put("name", unit.getName());
            units.add(item);
        }
        return units;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listOtherUnitPersonnel(long ownUnitId, long sourceUnitId, String query) {
        requireUnit(ownUnitId);
        Unit sourceUnit = unitRepository
                .findVisibleById(sourceUnitId, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        if (sourceUnit.getId() == ownUnitId) {
            throw new IllegalArgumentException("Bitte eine andere Einheit wählen.");
        }
        boolean testData = testModeService.isEnabled();
        String normalized = query != null ? query.trim() : "";
        List<Person> persons;
        if (normalized.length() < 2) {
            persons = personRepository.findActiveByUnitIdWithUnit(sourceUnitId, testData);
        } else {
            persons = personRepository.searchActiveByUnitId(sourceUnitId, normalized, testData);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Person person : persons) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", person.getId());
            item.put("displayName", person.displayName());
            item.put("unitId", sourceUnitId);
            item.put("unitName", sourceUnit.getName());
            result.add(item);
        }
        return result;
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
