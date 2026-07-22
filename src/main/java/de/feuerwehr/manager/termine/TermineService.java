package de.feuerwehr.manager.termine;

import de.feuerwehr.manager.berichte.AnwesenheitslisteTerminSyncService;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
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

    private static final DateTimeFormatter DASHBOARD_DAY_FMT = DateTimeFormatter.ofPattern("dd");
    private static final DateTimeFormatter DASHBOARD_MONTH_FMT =
            DateTimeFormatter.ofPattern("MMM", Locale.GERMANY);
    private static final DateTimeFormatter DASHBOARD_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final UnitTerminRepository unitTerminRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final PersonalService personalService;
    private final PersonGroupRepository personGroupRepository;
    private final PersonalInstructorGroupService personalInstructorGroupService;
    private final TestModeService testModeService;
    private final AnwesenheitslisteTerminSyncService anwesenheitslisteTerminSyncService;

    @Transactional(readOnly = true)
    public List<DienstplanTerminView> listDienstplanTermine(long unitId) {
        return listTermineByCategory(unitId, TermineCategory.DIENSTPLAN);
    }

    @Transactional(readOnly = true)
    public List<DienstplanTerminView> listSonstigesTermine(long unitId) {
        return listTermineByCategory(unitId, TermineCategory.SONSTIGES);
    }

    @Transactional(readOnly = true)
    public List<DienstplanTerminView> listSonderdienstTermine(long unitId) {
        return listTermineByCategory(unitId, TermineCategory.SONDERDIENST);
    }

    @Transactional(readOnly = true)
    public List<DienstplanTerminView> listTermineByCategory(long unitId, TermineCategory category) {
        List<UnitTermin> termins = unitTerminRepository.findByUnitAndCategoryWithInstructor(unitId, category);
        termins.forEach(this::touchTerminCollections);
        return termins.stream().map(this::toDienstplanView).toList();
    }

    @Transactional(readOnly = true)
    public List<MeineTerminView> listMyTermine(long unitId, long personId) {
        List<UnitTermin> termins = unitTerminRepository.findMineByUnitAndPerson(unitId, personId);
        termins.forEach(this::touchTerminCollections);
        return termins.stream().map(this::toMeineTerminView).toList();
    }

    @Transactional(readOnly = true)
    public List<DashboardTerminWidgetView> listUpcomingDashboardTermine(long unitId, long personId, int limit) {
        LocalDateTime now = LocalDateTime.now();
        return listMyTermine(unitId, personId).stream()
                .filter(termin -> !termin.startAt().isBefore(now))
                .sorted(Comparator.comparing(MeineTerminView::startAt))
                .limit(Math.max(limit, 0))
                .map(this::toDashboardTerminWidgetView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> listKnownDienstplanThemen(long unitId) {
        LinkedHashSet<String> themen = new LinkedHashSet<>();
        listKnownTitles(unitId, TermineCategory.DIENSTPLAN).forEach(themen::add);
        personalInstructorGroupService.listThemen(unitId).forEach(themen::add);
        return themen.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    @Transactional(readOnly = true)
    public List<String> listKnownSonstigesBeschreibungen(long unitId) {
        return listKnownTitles(unitId, TermineCategory.SONSTIGES);
    }

    @Transactional(readOnly = true)
    public List<String> listKnownSonderdienstBeschreibungen(long unitId) {
        return listKnownTitles(unitId, TermineCategory.SONDERDIENST);
    }

    @Transactional(readOnly = true)
    public List<String> listKnownTitles(long unitId, TermineCategory category) {
        return unitTerminRepository.findDistinctTitlesByUnitAndCategory(unitId, category).stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional
    public void createDienstplanTermin(long unitId, long userId, CreateDienstplanTerminRequest request) {
        createTermin(unitId, userId, TermineCategory.DIENSTPLAN, request);
    }

    @Transactional
    public void createSonstigesTermin(long unitId, long userId, CreateDienstplanTerminRequest request) {
        createTermin(unitId, userId, TermineCategory.SONSTIGES, request);
    }

    @Transactional
    public void createSonderdienstTermin(long unitId, long userId, CreateDienstplanTerminRequest request) {
        createTermin(unitId, userId, TermineCategory.SONDERDIENST, request);
    }

    @Transactional
    public void createTermin(
            long unitId, long userId, TermineCategory category, CreateDienstplanTerminRequest request) {
        ParsedDienstplanFields parsed = parseDienstplanRequest(request);
        Unit unit = unitRepository
                .findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        User createdBy = userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden."));
        UnitTermin termin = new UnitTermin();
        termin.setUnit(unit);
        termin.setCategory(category);
        termin.setCreatedBy(createdBy);
        applyTerminFields(unitId, termin, parsed, request);
        unitTerminRepository.save(termin);
        anwesenheitslisteTerminSyncService.syncSingleTermin(unitId, termin);
    }

    @Transactional
    public void updateDienstplanTermin(long unitId, long terminId, CreateDienstplanTerminRequest request) {
        updateTermin(unitId, terminId, TermineCategory.DIENSTPLAN, request);
    }

    @Transactional
    public void updateSonstigesTermin(long unitId, long terminId, CreateDienstplanTerminRequest request) {
        updateTermin(unitId, terminId, TermineCategory.SONSTIGES, request);
    }

    @Transactional
    public void updateSonderdienstTermin(long unitId, long terminId, CreateDienstplanTerminRequest request) {
        updateTermin(unitId, terminId, TermineCategory.SONDERDIENST, request);
    }

    @Transactional
    public void updateTermin(
            long unitId, long terminId, TermineCategory category, CreateDienstplanTerminRequest request) {
        ParsedDienstplanFields parsed = parseDienstplanRequest(request);
        UnitTermin termin = requireCategoryTermin(unitId, terminId, category);
        applyTerminFields(unitId, termin, parsed, request);
        unitTerminRepository.save(termin);
        anwesenheitslisteTerminSyncService.syncSingleTermin(unitId, termin);
    }

    @Transactional
    public void deleteDienstplanTermin(long unitId, long terminId) {
        deleteTermin(unitId, terminId, TermineCategory.DIENSTPLAN);
    }

    @Transactional
    public void deleteSonstigesTermin(long unitId, long terminId) {
        deleteTermin(unitId, terminId, TermineCategory.SONSTIGES);
    }

    @Transactional
    public void deleteSonderdienstTermin(long unitId, long terminId) {
        deleteTermin(unitId, terminId, TermineCategory.SONDERDIENST);
    }

    @Transactional
    public void deleteTermin(long unitId, long terminId, TermineCategory category) {
        UnitTermin termin = requireCategoryTermin(unitId, terminId, category);
        anwesenheitslisteTerminSyncService.onTerminDeleted(unitId, terminId);
        unitTerminRepository.delete(termin);
    }

    private UnitTermin requireCategoryTermin(long unitId, long terminId, TermineCategory category) {
        UnitTermin termin = unitTerminRepository
                .findByIdAndUnitId(terminId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Termin nicht gefunden."));
        if (termin.getCategory() != category) {
            throw new IllegalArgumentException("Termin nicht gefunden.");
        }
        touchTerminCollections(termin);
        return termin;
    }

    private void applyTerminFields(
            long unitId, UnitTermin termin, ParsedDienstplanFields parsed, CreateDienstplanTerminRequest request) {
        termin.setTitle(parsed.thema());
        termin.setStartAt(parsed.startAt());
        termin.setEndAt(parsed.endAt());
        termin.setInstructorPersons(resolveAssignedPersons(unitId, request.instructorPersonIds()));
        applyAudience(unitId, termin, request);
    }

    private ParsedDienstplanFields parseDienstplanRequest(CreateDienstplanTerminRequest request) {
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
            throw new IllegalArgumentException("Bitte ein Thema bzw. eine Beschreibung angeben.");
        }
        LocalDateTime startAt = LocalDateTime.of(request.terminDatum(), request.dienstBeginn());
        LocalDateTime endAt = LocalDateTime.of(request.terminDatum(), request.dienstEnde());
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Dienstende muss nach Dienstbeginn liegen.");
        }
        return new ParsedDienstplanFields(thema, startAt, endAt);
    }

    private record ParsedDienstplanFields(String thema, LocalDateTime startAt, LocalDateTime endAt) {}

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
        return new DienstplanTerminView(
                termin.getId(),
                termin.getStartAt().toLocalDate(),
                termin.getTitle(),
                termin.getStartAt().toLocalTime(),
                termin.getEndAt() != null ? termin.getEndAt().toLocalTime() : null,
                formatInstructorLabel(termin),
                formatAudienceLabel(termin),
                termin.isAudienceAll(),
                termin.getInstructorPersons().stream().map(Person::getId).toList(),
                termin.getAssignedGroups().stream().map(PersonGroup::getId).toList(),
                termin.getAssignedPersons().stream().map(Person::getId).toList());
    }

    private MeineTerminView toMeineTerminView(UnitTermin termin) {
        return new MeineTerminView(
                termin.getId(),
                termin.getCategory(),
                termin.getCategory().displayLabel(),
                termin.getStartAt().toLocalDate(),
                termin.getTitle(),
                termin.getStartAt().toLocalTime(),
                termin.getEndAt() != null ? termin.getEndAt().toLocalTime() : null,
                formatInstructorLabel(termin),
                termin.getStartAt(),
                termin.getEndAt() != null ? termin.getEndAt() : termin.getStartAt());
    }

    private DashboardTerminWidgetView toDashboardTerminWidgetView(MeineTerminView termin) {
        return new DashboardTerminWidgetView(
                termin.thema(),
                DASHBOARD_DAY_FMT.format(termin.datum()),
                DASHBOARD_MONTH_FMT.format(termin.datum()),
                DASHBOARD_TIME_FMT.format(termin.beginn()),
                termin.categoryLabel(),
                termin.category());
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
