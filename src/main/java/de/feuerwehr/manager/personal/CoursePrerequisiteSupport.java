package de.feuerwehr.manager.personal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CoursePrerequisiteSupport {

    private CoursePrerequisiteSupport() {}

    static Set<Long> canonicalIds(Course course) {
        Set<Long> ids = new HashSet<>();
        if (course == null || course.getId() == null) {
            return ids;
        }
        ids.add(course.getId());
        if (course.getProductionSourceId() != null) {
            ids.add(course.getProductionSourceId());
        }
        return ids;
    }

    static boolean hasAllPrerequisites(Set<Long> completedCanonicalIds, Collection<Course> prerequisites) {
        if (prerequisites == null || prerequisites.isEmpty()) {
            return true;
        }
        if (completedCanonicalIds == null || completedCanonicalIds.isEmpty()) {
            return false;
        }
        for (Course required : prerequisites) {
            if (!hasCourse(completedCanonicalIds, required)) {
                return false;
            }
        }
        return true;
    }

    static List<Course> missingPrerequisites(Set<Long> completedCanonicalIds, Collection<Course> prerequisites) {
        if (prerequisites == null || prerequisites.isEmpty()) {
            return List.of();
        }
        List<Course> missing = new ArrayList<>();
        for (Course required : prerequisites) {
            if (!hasCourse(completedCanonicalIds, required)) {
                missing.add(required);
            }
        }
        return missing;
    }

    static boolean isIgnored(Course course, Collection<Long> ignoredIds) {
        if (course == null || course.getId() == null || ignoredIds == null || ignoredIds.isEmpty()) {
            return false;
        }
        for (Long id : ignoredIds) {
            if (id == null) {
                continue;
            }
            if (id.equals(course.getId()) || id.equals(course.getProductionSourceId())) {
                return true;
            }
        }
        return false;
    }

    static List<Course> enforcedPrerequisites(
            Collection<Course> prerequisites, Collection<Long> ignoredIds, boolean ignoreAll) {
        if (prerequisites == null || prerequisites.isEmpty() || ignoreAll) {
            return List.of();
        }
        List<Course> enforced = new ArrayList<>();
        for (Course required : prerequisites) {
            if (!isIgnored(required, ignoredIds)) {
                enforced.add(required);
            }
        }
        return enforced;
    }

    static boolean hasCourse(Set<Long> completedCanonicalIds, Course course) {
        if (completedCanonicalIds == null || course == null || course.getId() == null) {
            return false;
        }
        if (completedCanonicalIds.contains(course.getId())) {
            return true;
        }
        return course.getProductionSourceId() != null
                && completedCanonicalIds.contains(course.getProductionSourceId());
    }

    static boolean createsCycle(long courseId, Collection<Long> newPrerequisiteIds, Map<Long, Set<Long>> graph) {
        if (newPrerequisiteIds == null || newPrerequisiteIds.isEmpty()) {
            return false;
        }
        Set<Long> next = new HashSet<>();
        for (Long id : newPrerequisiteIds) {
            if (id == null) {
                continue;
            }
            if (id == courseId) {
                return true;
            }
            next.add(id);
        }
        for (Long start : next) {
            if (reaches(start, courseId, graph, next, courseId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean reaches(
            long start, long target, Map<Long, Set<Long>> graph, Set<Long> overrideForCourse, long overrideCourseId) {
        Deque<Long> stack = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            long current = stack.pop();
            if (!seen.add(current)) {
                continue;
            }
            if (current == target) {
                return true;
            }
            Set<Long> edges = current == overrideCourseId ? overrideForCourse : graph.getOrDefault(current, Set.of());
            for (Long next : edges) {
                if (next != null) {
                    stack.push(next);
                }
            }
        }
        return false;
    }
}
