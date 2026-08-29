package de.feuerwehr.manager.personal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CoursePrerequisiteSupportTest {

    @Test
    void emptyPrerequisitesAreSatisfied() {
        assertTrue(CoursePrerequisiteSupport.hasAllPrerequisites(Set.of(), Set.of()));
        assertTrue(CoursePrerequisiteSupport.hasAllPrerequisites(Set.of(1L), Set.of()));
    }

    @Test
    void allPrerequisitesMustBeCompleted() {
        Course trupp = course(1L, null);
        Course atemschutz = course(2L, null);
        assertFalse(CoursePrerequisiteSupport.hasAllPrerequisites(Set.of(1L), Set.of(trupp, atemschutz)));
        assertTrue(CoursePrerequisiteSupport.hasAllPrerequisites(Set.of(1L, 2L), Set.of(trupp, atemschutz)));
    }

    @Test
    void productionSourceIdsCountAsSameCourse() {
        Course required = course(20L, 5L);
        assertTrue(CoursePrerequisiteSupport.hasCourse(Set.of(5L), required));
        assertTrue(CoursePrerequisiteSupport.hasCourse(Set.of(20L), required));
        assertFalse(CoursePrerequisiteSupport.hasCourse(Set.of(7L), required));
    }

    @Test
    void selfPrerequisiteIsCycle() {
        assertTrue(CoursePrerequisiteSupport.createsCycle(3L, Set.of(3L), Map.of()));
    }

    @Test
    void reversePrerequisiteIsCycle() {
        Map<Long, Set<Long>> graph = Map.of(1L, Set.of(2L));
        assertTrue(CoursePrerequisiteSupport.createsCycle(2L, Set.of(1L), graph));
        assertFalse(CoursePrerequisiteSupport.createsCycle(2L, Set.of(3L), graph));
    }

    @Test
    void missingPrerequisitesListsOnlyIncomplete() {
        Course trupp = course(1L, null);
        Course atemschutz = course(2L, null);
        List<Course> missing =
                CoursePrerequisiteSupport.missingPrerequisites(Set.of(1L), List.of(trupp, atemschutz));
        assertEquals(1, missing.size());
        assertEquals(2L, missing.get(0).getId());
    }

    @Test
    void ignoredPrerequisiteIsNotEnforced() {
        Course trupp = course(1L, null);
        Course atemschutz = course(2L, null);
        List<Course> enforced =
                CoursePrerequisiteSupport.enforcedPrerequisites(List.of(trupp, atemschutz), Set.of(2L), false);
        assertEquals(1, enforced.size());
        assertEquals(1L, enforced.get(0).getId());
        assertTrue(CoursePrerequisiteSupport.enforcedPrerequisites(List.of(trupp, atemschutz), Set.of(), true)
                .isEmpty());
    }

    @Test
    void isIgnoredMatchesIdAndProductionSource() {
        Course shadow = course(20L, 5L);
        assertTrue(CoursePrerequisiteSupport.isIgnored(shadow, Set.of(20L)));
        assertTrue(CoursePrerequisiteSupport.isIgnored(shadow, Set.of(5L)));
        assertFalse(CoursePrerequisiteSupport.isIgnored(shadow, Set.of(7L)));
    }

    private static Course course(long id, Long productionSourceId) {
        Course course = new Course();
        course.setId(id);
        course.setProductionSourceId(productionSourceId);
        return course;
    }
}
