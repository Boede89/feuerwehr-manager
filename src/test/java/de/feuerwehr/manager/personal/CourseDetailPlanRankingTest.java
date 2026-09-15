package de.feuerwehr.manager.personal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CourseDetailPlanRankingTest {

    @Test
    void firstGenerateUsesRankedOrder() {
        assertEquals(List.of(3L, 1L, 2L), CourseDetailPlanRanking.mergeOrder(List.of(), List.of(3L, 1L, 2L), false));
    }

    @Test
    void resortReplacesManualOrder() {
        assertEquals(
                List.of(2L, 1L),
                CourseDetailPlanRanking.mergeOrder(List.of(1L, 2L), List.of(2L, 1L), true));
    }

    @Test
    void keepsManualOrderAndAppendsNewcomers() {
        assertEquals(
                List.of(1L, 3L, 2L),
                CourseDetailPlanRanking.mergeOrder(List.of(1L, 3L), List.of(2L, 1L, 3L), false));
    }

    @Test
    void dropsPeopleWhoAreNoLongerCandidates() {
        assertEquals(List.of(2L), CourseDetailPlanRanking.mergeOrder(List.of(1L, 2L), List.of(2L), false));
    }

    @Test
    void ensurePrerequisitesFirstKeepsRelativeOrder() {
        Person a = person(1, "A");
        Person b = person(2, "B");
        Person c = person(3, "C");
        Map<Long, PersonalService.CoursePlanCandidate> candidates = Map.of(
                1L, new PersonalService.CoursePlanCandidate(a, false, List.of("Truppmann")),
                2L, new PersonalService.CoursePlanCandidate(b, true, List.of()),
                3L, new PersonalService.CoursePlanCandidate(c, true, List.of()));
        assertEquals(
                List.of(2L, 3L, 1L),
                CourseDetailPlanRanking.ensurePrerequisitesFirst(List.of(1L, 2L, 3L), candidates));
    }

    @Test
    void participationYearIsPreviousYearCappedAtCurrent() {
        int current = java.time.Year.now().getValue();
        assertEquals(current, CourseDetailPlanService.participationYearFor(current + 1));
        assertEquals(current - 1, CourseDetailPlanService.participationYearFor(current));
    }

    private static Person person(long id, String lastName) {
        Person person = new Person();
        person.setId(id);
        person.setLastName(lastName);
        person.setFirstName("X");
        return person;
    }
}
