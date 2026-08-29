package de.feuerwehr.manager.personal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.feuerwehr.manager.personal.PersonalService.CoursePlanResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoursePlanResultTest {

    @Test
    void ignoresPrerequisiteAcceptsTemplateIntegerIds() {
        Course trupp = course(7L, "Truppmann");
        CoursePlanResult result = new CoursePlanResult(trupp, List.of(trupp), List.of(7L), List.of(), 0, 0, 0, false);
        assertTrue(result.ignoresPrerequisite(Integer.valueOf(7)));
        assertTrue(result.ignoresPrerequisite(7L));
        assertFalse(result.ignoresPrerequisite(Integer.valueOf(8)));
        assertFalse(result.ignoresPrerequisite(null));
    }

    @Test
    void toggledIgnoreIdsAddsAndRemoves() {
        Course trupp = course(7L, "Truppmann");
        CoursePlanResult empty = new CoursePlanResult(trupp, List.of(trupp), List.of(), List.of(), 0, 0, 0, false);
        assertEquals(List.of(7L), empty.toggledIgnoreIds(Integer.valueOf(7)));

        CoursePlanResult active = new CoursePlanResult(trupp, List.of(trupp), List.of(7L), List.of(), 0, 0, 0, false);
        assertEquals(List.of(), active.toggledIgnoreIds(7L));
    }

    private static Course course(long id, String name) {
        Course course = new Course();
        course.setId(id);
        course.setName(name);
        return course;
    }
}
