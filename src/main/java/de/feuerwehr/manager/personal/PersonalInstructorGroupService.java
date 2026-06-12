package de.feuerwehr.manager.personal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class PersonalInstructorGroupService {

    private final InstructorGroupRepository groupRepository;
    private final PersonRepository personRepository;
    private final UnitRepository unitRepository;
    private final TestModeService testModeService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<InstructorGroup> listGroups(long unitId) {
        if (!testModeService.isEnabled()) {
            return groupRepository.findByUnitIdWithMembers(unitId, false);
        }
        List<InstructorGroup> merged = new ArrayList<>(groupRepository.findByUnitIdWithMembers(unitId, false));
        merged.addAll(groupRepository.findByUnitIdWithMembers(unitId, true));
        merged.sort(Comparator.comparing(InstructorGroup::getThema, String.CASE_INSENSITIVE_ORDER));
        return merged;
    }

    @Transactional(readOnly = true)
    public List<String> listThemen(long unitId) {
        return listGroups(unitId).stream()
                .map(InstructorGroup::getThema)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> listInstructorPersonIdsForThema(long unitId, String thema) {
        String normalized = normalizeThema(thema);
        if (normalized == null) {
            return List.of();
        }
        Set<Long> personIds = new LinkedHashSet<>();
        for (InstructorGroup group : listGroups(unitId)) {
            if (group.getThema().equalsIgnoreCase(normalized)) {
                group.getMembers().forEach(person -> personIds.add(person.getId()));
            }
        }
        return List.copyOf(personIds);
    }

    @Transactional(readOnly = true)
    public List<InstructorGroupTerminPayload> listGroupsForTermin(long unitId) {
        return listGroups(unitId).stream()
                .map(group -> new InstructorGroupTerminPayload(
                        group.getThema(),
                        group.getMembers().stream().map(Person::getId).toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public String serializeGroupsForTerminJson(long unitId) {
        try {
            return objectMapper.writeValueAsString(listGroupsForTermin(unitId));
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @Transactional
    public InstructorGroup createGroup(long unitId, String thema, List<Long> personIds) {
        String normalizedThema = validateThema(thema);
        Unit unit = requireUnit(unitId);
        boolean testData = testModeService.isEnabled();
        if (groupRepository.existsByUnitIdAndThemaIgnoreCaseAndTestData(unitId, normalizedThema, testData)) {
            throw new IllegalArgumentException("Eine Ausbildergruppe mit diesem Thema existiert bereits.");
        }
        InstructorGroup group = new InstructorGroup();
        group.setUnit(unit);
        group.setThema(normalizedThema);
        group.setTestData(testData);
        group.setMembers(resolveMembers(unitId, personIds, testData));
        return groupRepository.save(group);
    }

    @Transactional
    public InstructorGroup updateGroup(long groupId, String thema, List<Long> personIds) {
        String normalizedThema = validateThema(thema);
        InstructorGroup group = requireWritableGroup(groupId);
        boolean testData = testModeService.isEnabled();
        if (groupRepository.existsByUnitIdAndThemaIgnoreCaseAndTestDataAndIdNot(
                group.getUnit().getId(), normalizedThema, testData, group.getId())) {
            throw new IllegalArgumentException("Eine Ausbildergruppe mit diesem Thema existiert bereits.");
        }
        group.setThema(normalizedThema);
        group.getMembers().clear();
        group.getMembers().addAll(resolveMembers(group.getUnit().getId(), personIds, testData));
        return groupRepository.save(group);
    }

    @Transactional
    public void deleteGroup(long groupId) {
        InstructorGroup group = requireWritableGroup(groupId);
        groupRepository.delete(group);
    }

    private InstructorGroup requireWritableGroup(long groupId) {
        if (!testModeService.isEnabled()) {
            return groupRepository
                    .findByIdWithMembers(groupId, false)
                    .orElseThrow(() -> new IllegalArgumentException("Ausbildergruppe nicht gefunden."));
        }
        return groupRepository
                .findByIdWithMembers(groupId, true)
                .orElseThrow(() -> new IllegalArgumentException("Ausbildergruppe nicht gefunden."));
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

    private static String validateThema(String thema) {
        String normalized = normalizeThema(thema);
        if (normalized == null) {
            throw new IllegalArgumentException("Bitte ein Thema angeben.");
        }
        if (normalized.length() > 150) {
            throw new IllegalArgumentException("Thema darf maximal 150 Zeichen lang sein.");
        }
        return normalized;
    }

    private static String normalizeThema(String thema) {
        if (thema == null) {
            return null;
        }
        String trimmed = thema.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record InstructorGroupTerminPayload(String thema, List<Long> personIds) {}
}
