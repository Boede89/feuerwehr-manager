package de.feuerwehr.manager.personal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static Course course(long id, Long productionSourceId) {
        Course course = new Course();
        course.setId(id);
        course.setProductionSourceId(productionSourceId);
        return course;
    }
}
