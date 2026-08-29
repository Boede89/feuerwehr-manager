package de.feuerwehr.manager.personal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class CourseDetailPlanRanking {

    private CourseDetailPlanRanking() {}

    static List<Long> mergeOrder(List<Long> previousOrder, List<Long> rankedCandidates, boolean resort) {
        if (rankedCandidates == null || rankedCandidates.isEmpty()) {
            return List.of();
        }
        if (resort || previousOrder == null || previousOrder.isEmpty()) {
            return List.copyOf(rankedCandidates);
        }
        Set<Long> current = new LinkedHashSet<>(rankedCandidates);
        List<Long> result = new ArrayList<>();
        for (Long id : previousOrder) {
            if (id != null && current.contains(id) && !result.contains(id)) {
                result.add(id);
            }
        }
        for (Long id : rankedCandidates) {
            if (id != null && !result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }
}
