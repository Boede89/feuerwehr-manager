package de.feuerwehr.manager.personal;

import de.feuerwehr.manager.auswertung.AuswertungPersonRow;
import de.feuerwehr.manager.auswertung.AuswertungService;
import de.feuerwehr.manager.personal.PersonalService.CoursePlanCandidate;
import de.feuerwehr.manager.personal.PersonalService.CoursePlanResult;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseDetailPlanService {

    private final CourseDetailPlanRepository planRepository;
    private final CourseDetailPlanItemRepository itemRepository;
    private final CourseDetailPlanEntryRepository entryRepository;
    private final PersonalService personalService;
    private final AuswertungService auswertungService;
    private final TestModeService testModeService;

    public static int defaultPlanYear() {
        return Year.now().getValue() + 1;
    }

    public static int participationYearFor(int planYear) {
        int current = Year.now().getValue();
        int previous = planYear - 1;
        if (previous < 2000) {
            return current;
        }
        return Math.min(previous, current);
    }

    @Transactional(readOnly = true)
    public List<Integer> yearOptions(long unitId) {
        int current = Year.now().getValue();
        Set<Integer> years = new HashSet<>();
        years.add(current - 1);
        years.add(current);
        years.add(current + 1);
        years.add(current + 2);
        years.addAll(planRepository.findPlanYearsByUnitIdAndTestData(unitId, testModeService.isEnabled()));
        return years.stream().sorted(Comparator.reverseOrder()).toList();
    }

    @Transactional(readOnly = true)
    public DetailPlanView loadView(long unitId, int planYear) {
        Unit unit = personalService.requireUnit(unitId);
        List<Course> courses = personalService.listCourses(unitId, false);
        CourseDetailPlan plan = planRepository
                .findWithItemsByUnitIdAndPlanYearAndTestData(unitId, planYear, testModeService.isEnabled())
                .orElse(null);
        int participationYear = participationYearFor(planYear);
        Map<Long, AuswertungPersonRow> participation = Map.of();
        if (plan != null && plan.isUseParticipation()) {
            participation = participationByPersonId(unitId, participationYear);
        }
        List<CourseItemView> items = List.of();
        if (plan != null) {
            items = toItemViews(unitId, plan, participation);
        }
        return new DetailPlanView(
                unit, planYear, participationYear, plan, courses, items, selectedCourseIds(plan), seatsByCourseId(plan));
    }

    @Transactional
    public CourseDetailPlan saveAndGenerate(
            long unitId,
            int planYear,
            boolean useParticipation,
            List<CourseSeatInput> courseSeats,
            boolean resort) {
        if (planYear < 2000 || planYear > 2100) {
            throw new IllegalArgumentException("Ungültiges Planungsjahr.");
        }
        Unit unit = personalService.requireUnit(unitId);
        List<CourseSeatInput> selected = courseSeats == null
                ? List.of()
                : courseSeats.stream().filter(row -> row != null && row.seats() > 0).toList();
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens einen Lehrgang mit Platzanzahl auswählen.");
        }

        boolean testData = testModeService.isEnabled();
        CourseDetailPlan plan = planRepository
                .findWithItemsByUnitIdAndPlanYearAndTestData(unitId, planYear, testData)
                .orElseGet(() -> {
                    CourseDetailPlan created = new CourseDetailPlan();
                    created.setUnit(unit);
                    created.setPlanYear(planYear);
                    created.setTestData(testData);
                    created.setItems(new ArrayList<>());
                    return created;
                });
        boolean created = plan.getId() == null;
        boolean participationChanged = !created && plan.isUseParticipation() != useParticipation;
        plan.setUseParticipation(useParticipation);
        syncItems(plan, unitId, selected);
        CourseDetailPlan saved = planRepository.saveAndFlush(plan);
        rebuildEntries(unitId, saved, resort || created || participationChanged);
        return saved;
    }

    @Transactional
    public void moveEntry(long unitId, long entryId, String direction) {
        CourseDetailPlanEntry entry = requireEntry(unitId, entryId);
        CourseDetailPlanItem item = entry.getItem();
        List<CourseDetailPlanEntry> rows = new ArrayList<>(loadEntries(List.of(item.getId())));
        int idx = indexOfEntry(rows, entryId);
        if (idx < 0) {
            throw new IllegalArgumentException("Eintrag nicht gefunden");
        }
        int delta = "down".equalsIgnoreCase(direction != null ? direction.trim() : "") ? 1 : -1;
        int next = idx + delta;
        if (next < 0 || next >= rows.size()) {
            return;
        }
        Collections.swap(rows, idx, next);
        persistOrder(rows);
    }

    @Transactional
    public void reorderEntries(long unitId, long itemId, List<Long> entryIds) {
        CourseDetailPlanItem item = itemRepository
                .findByIdWithPlan(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Lehrgang nicht in der Planung"));
        requirePlanUnit(item.getPlan(), unitId);
        if (entryIds == null || entryIds.isEmpty()) {
            return;
        }
        List<CourseDetailPlanEntry> rows = loadEntries(List.of(item.getId()));
        Map<Long, CourseDetailPlanEntry> byId = new HashMap<>();
        for (CourseDetailPlanEntry row : rows) {
            byId.put(row.getId(), row);
        }
        List<CourseDetailPlanEntry> ordered = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long id : entryIds) {
            CourseDetailPlanEntry row = byId.get(id);
            if (row != null && seen.add(id)) {
                ordered.add(row);
            }
        }
        for (CourseDetailPlanEntry row : rows) {
            if (seen.add(row.getId())) {
                ordered.add(row);
            }
        }
        persistOrder(ordered);
    }

    @Transactional
    public void setConfirmed(long unitId, long entryId, boolean confirmed) {
        CourseDetailPlanEntry entry = requireEntry(unitId, entryId);
        entry.setConfirmed(confirmed);
        entryRepository.save(entry);
    }

    @Transactional
    public void deletePlan(long unitId, int planYear) {
        CourseDetailPlan plan = planRepository
                .findByUnitIdAndPlanYearAndTestData(unitId, planYear, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Keine Planung für dieses Jahr."));
        requirePlanUnit(plan, unitId);
        planRepository.delete(plan);
    }

    private void syncItems(CourseDetailPlan plan, long unitId, List<CourseSeatInput> selected) {
        Map<Long, CourseDetailPlanItem> existing = new LinkedHashMap<>();
        for (CourseDetailPlanItem item : plan.getItems()) {
            if (item.getCourse() != null && item.getCourse().getId() != null) {
                existing.put(item.getCourse().getId(), item);
            }
        }
        Set<Long> keep = new HashSet<>();
        int order = 0;
        List<CourseDetailPlanItem> next = new ArrayList<>();
        for (CourseSeatInput input : selected) {
            Course course = personalService.requireCourseForRead(input.courseId(), unitId);
            keep.add(course.getId());
            CourseDetailPlanItem item = existing.get(course.getId());
            if (item == null) {
                item = new CourseDetailPlanItem();
                item.setPlan(plan);
                item.setCourse(course);
                item.setEntries(new ArrayList<>());
            }
            item.setSeats(input.seats());
            item.setSortOrder(order++);
            next.add(item);
        }
        plan.getItems().removeIf(item -> item.getCourse() == null
                || item.getCourse().getId() == null
                || !keep.contains(item.getCourse().getId()));
        for (CourseDetailPlanItem item : next) {
            if (!plan.getItems().contains(item)) {
                plan.getItems().add(item);
            }
        }
    }

    private void rebuildEntries(long unitId, CourseDetailPlan plan, boolean resort) {
        int participationYear = participationYearFor(plan.getPlanYear());
        Map<Long, AuswertungPersonRow> participation = plan.isUseParticipation()
                ? participationByPersonId(unitId, participationYear)
                : Map.of();
        List<CourseDetailPlanItem> items = plan.getItems() == null ? List.of() : plan.getItems();
        List<Long> itemIds = items.stream().map(CourseDetailPlanItem::getId).filter(id -> id != null).toList();
        Map<Long, List<CourseDetailPlanEntry>> existingByItem = new HashMap<>();
        if (!itemIds.isEmpty()) {
            for (CourseDetailPlanEntry entry : loadEntries(itemIds)) {
                existingByItem
                        .computeIfAbsent(entry.getItem().getId(), ignored -> new ArrayList<>())
                        .add(entry);
            }
        }
        for (CourseDetailPlanItem item : items) {
            if (item.getCourse() == null || item.getCourse().getId() == null) {
                continue;
            }
            CoursePlanResult planResult =
                    personalService.planCourse(unitId, item.getCourse().getId(), true);
            Map<Long, CoursePlanCandidate> candidatesByPerson = new LinkedHashMap<>();
            for (CoursePlanCandidate candidate : planResult.candidates()) {
                if (candidate.person() != null && candidate.person().getId() != null) {
                    candidatesByPerson.put(candidate.person().getId(), candidate);
                }
            }
            List<CoursePlanCandidate> ranked = new ArrayList<>(candidatesByPerson.values());
            ranked.sort(candidateComparator(participation, plan.isUseParticipation()));
            List<Long> rankedIds = ranked.stream().map(row -> row.person().getId()).toList();
            List<CourseDetailPlanEntry> previous = existingByItem.getOrDefault(item.getId(), List.of());
            List<Long> previousIds = previous.stream()
                    .map(row -> row.getPerson() != null ? row.getPerson().getId() : null)
                    .filter(id -> id != null)
                    .toList();
            Map<Long, CourseDetailPlanEntry> previousByPerson = new HashMap<>();
            for (CourseDetailPlanEntry entry : previous) {
                if (entry.getPerson() != null && entry.getPerson().getId() != null) {
                    previousByPerson.put(entry.getPerson().getId(), entry);
                }
            }
            List<Long> nextIds = CourseDetailPlanRanking.mergeOrder(previousIds, rankedIds, resort);
            Set<Long> keepPeople = new HashSet<>(nextIds);
            if (item.getEntries() == null) {
                item.setEntries(new ArrayList<>());
            }
            item.getEntries().removeIf(entry -> entry.getPerson() == null
                    || entry.getPerson().getId() == null
                    || !keepPeople.contains(entry.getPerson().getId()));
            int sort = 0;
            for (Long personId : nextIds) {
                CourseDetailPlanEntry entry = previousByPerson.get(personId);
                if (entry == null) {
                    CoursePlanCandidate candidate = candidatesByPerson.get(personId);
                    if (candidate == null || candidate.person() == null) {
                        continue;
                    }
                    entry = new CourseDetailPlanEntry();
                    entry.setItem(item);
                    entry.setPerson(candidate.person());
                    entry.setConfirmed(false);
                    item.getEntries().add(entry);
                }
                entry.setSortOrder(sort++);
            }
        }
        planRepository.save(plan);
    }

    private List<CourseItemView> toItemViews(
            long unitId, CourseDetailPlan plan, Map<Long, AuswertungPersonRow> participation) {
        List<CourseDetailPlanItem> items = plan.getItems() == null ? List.of() : plan.getItems();
        List<Long> itemIds = items.stream().map(CourseDetailPlanItem::getId).filter(id -> id != null).toList();
        Map<Long, List<CourseDetailPlanEntry>> entriesByItem = new HashMap<>();
        if (!itemIds.isEmpty()) {
            for (CourseDetailPlanEntry entry : loadEntries(itemIds)) {
                entriesByItem
                        .computeIfAbsent(entry.getItem().getId(), ignored -> new ArrayList<>())
                        .add(entry);
            }
        }
        List<CourseDetailPlanItem> ordered = new ArrayList<>(items);
        ordered.sort(Comparator.comparingInt(CourseDetailPlanItem::getSortOrder));
        List<CourseItemView> views = new ArrayList<>();
        for (CourseDetailPlanItem item : ordered) {
            if (item.getCourse() == null) {
                continue;
            }
            CoursePlanResult planResult = personalService.planCourse(unitId, item.getCourse().getId(), true);
            Map<Long, CoursePlanCandidate> byPerson = new HashMap<>();
            for (CoursePlanCandidate candidate : planResult.candidates()) {
                if (candidate.person() != null && candidate.person().getId() != null) {
                    byPerson.put(candidate.person().getId(), candidate);
                }
            }
            List<CourseDetailPlanEntry> rows = entriesByItem.getOrDefault(item.getId(), List.of());
            List<EntryView> entryViews = new ArrayList<>();
            int rank = 1;
            for (CourseDetailPlanEntry entry : rows) {
                Person person = entry.getPerson();
                if (person == null) {
                    continue;
                }
                CoursePlanCandidate candidate = byPerson.get(person.getId());
                AuswertungPersonRow stats = participation.get(person.getId());
                boolean withinSeats = rank <= item.getSeats();
                entryViews.add(new EntryView(
                        entry.getId(),
                        rank,
                        person,
                        withinSeats,
                        entry.isConfirmed(),
                        candidate == null || candidate.prerequisitesMet(),
                        candidate == null ? List.of() : candidate.missingPrerequisiteNames(),
                        candidate == null ? "—" : candidate.missingPrerequisitesLabel(),
                        stats == null ? "—" : stats.dienstbeteiligung(),
                        stats == null ? 0 : stats.dienstPct()));
                rank++;
            }
            views.add(new CourseItemView(
                    item.getId(),
                    item.getCourse(),
                    item.getSeats(),
                    entryViews,
                    Math.min(item.getSeats(), entryViews.size())));
        }
        return views;
    }

    private Map<Long, AuswertungPersonRow> participationByPersonId(long unitId, int year) {
        Map<Long, AuswertungPersonRow> result = new HashMap<>();
        for (AuswertungPersonRow row : auswertungService.listPersonRows(unitId, year)) {
            result.put(row.personId(), row);
        }
        return result;
    }

    private Comparator<CoursePlanCandidate> candidateComparator(
            Map<Long, AuswertungPersonRow> participation, boolean useParticipation) {
        Comparator<CoursePlanCandidate> byName = Comparator.comparing(
                        (CoursePlanCandidate row) -> row.person().getLastName() != null
                                ? row.person().getLastName()
                                : "",
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(
                        row -> row.person().getFirstName() != null ? row.person().getFirstName() : "",
                        String.CASE_INSENSITIVE_ORDER);
        if (!useParticipation) {
            return byName;
        }
        return Comparator.comparingDouble((CoursePlanCandidate row) -> {
                    AuswertungPersonRow stats = participation.get(row.person().getId());
                    return stats == null ? 0 : stats.dienstPct();
                })
                .reversed()
                .thenComparing(byName);
    }

    private List<CourseDetailPlanEntry> loadEntries(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        return entryRepository.findByItemIdInWithPerson(itemIds);
    }

    private CourseDetailPlanEntry requireEntry(long unitId, long entryId) {
        CourseDetailPlanEntry entry = entryRepository
                .findByIdWithPlan(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Eintrag nicht gefunden"));
        requirePlanUnit(entry.getItem().getPlan(), unitId);
        return entry;
    }

    private static void requirePlanUnit(CourseDetailPlan plan, long unitId) {
        if (plan == null || plan.getUnit() == null || !plan.getUnit().getId().equals(unitId)) {
            throw new IllegalArgumentException("Planung gehört nicht zur Einheit");
        }
    }

    private static int indexOfEntry(List<CourseDetailPlanEntry> rows, long entryId) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getId() != null && rows.get(i).getId() == entryId) {
                return i;
            }
        }
        return -1;
    }

    private void persistOrder(List<CourseDetailPlanEntry> rows) {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setSortOrder(i);
            entryRepository.save(rows.get(i));
        }
    }

    public record CourseSeatInput(long courseId, int seats) {}

    public record EntryView(
            long entryId,
            int rank,
            Person person,
            boolean withinSeats,
            boolean confirmed,
            boolean prerequisitesMet,
            List<String> missingPrerequisiteNames,
            String missingPrerequisitesLabel,
            String dienstbeteiligung,
            double dienstPct) {}

    public record CourseItemView(
            long itemId, Course course, int seats, List<EntryView> entries, int assignedCount) {
        public int waitlistCount() {
            int total = entries == null ? 0 : entries.size();
            return Math.max(0, total - assignedCount);
        }

        public int confirmedCount() {
            if (entries == null || entries.isEmpty()) {
                return 0;
            }
            int count = 0;
            for (EntryView row : entries) {
                if (row.withinSeats() && row.confirmed()) {
                    count++;
                }
            }
            return count;
        }
    }

    public record DetailPlanView(
            Unit unit,
            int planYear,
            int participationYear,
            CourseDetailPlan plan,
            List<Course> availableCourses,
            List<CourseItemView> items,
            Set<Long> selectedCourseIds,
            Map<Long, Integer> seatsByCourseId) {
        public boolean includesCourse(Object courseId) {
            Long id = asLong(courseId);
            return id != null && selectedCourseIds != null && selectedCourseIds.contains(id);
        }

        public int seatsFor(Object courseId) {
            Long id = asLong(courseId);
            if (id == null || seatsByCourseId == null) {
                return 1;
            }
            Integer seats = seatsByCourseId.get(id);
            return seats == null || seats < 1 ? 1 : seats;
        }

        public int totalSeats() {
            if (items == null || items.isEmpty()) {
                return 0;
            }
            return items.stream().mapToInt(CourseItemView::seats).sum();
        }

        public int totalAssigned() {
            if (items == null || items.isEmpty()) {
                return 0;
            }
            return items.stream().mapToInt(CourseItemView::assignedCount).sum();
        }

        public int totalConfirmed() {
            if (items == null || items.isEmpty()) {
                return 0;
            }
            return items.stream().mapToInt(CourseItemView::confirmedCount).sum();
        }

        private static Long asLong(Object courseId) {
            if (courseId instanceof Number number) {
                return number.longValue();
            }
            if (courseId instanceof String raw && !raw.isBlank()) {
                try {
                    return Long.parseLong(raw.trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
    }

    private static Set<Long> selectedCourseIds(CourseDetailPlan plan) {
        if (plan == null || plan.getItems() == null) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        for (CourseDetailPlanItem item : plan.getItems()) {
            if (item.getCourse() != null && item.getCourse().getId() != null) {
                ids.add(item.getCourse().getId());
            }
        }
        return ids;
    }

    private static Map<Long, Integer> seatsByCourseId(CourseDetailPlan plan) {
        Map<Long, Integer> seats = new HashMap<>();
        if (plan == null || plan.getItems() == null) {
            return seats;
        }
        for (CourseDetailPlanItem item : plan.getItems()) {
            if (item.getCourse() != null && item.getCourse().getId() != null) {
                seats.put(item.getCourse().getId(), item.getSeats());
            }
        }
        return seats;
    }
}
