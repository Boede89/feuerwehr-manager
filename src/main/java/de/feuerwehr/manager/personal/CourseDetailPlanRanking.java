package de.feuerwehr.manager.personal;

import de.feuerwehr.manager.personal.PersonalService.CoursePlanCandidate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    /**
     * Hält Personen mit erfüllten Voraussetzungen oben, behält die relative Reihenfolge
     * innerhalb der beiden Gruppen.
     */
    static List<Long> ensurePrerequisitesFirst(
            List<Long> order, Map<Long, CoursePlanCandidate> candidatesByPerson) {
        if (order == null || order.isEmpty() || candidatesByPerson == null || candidatesByPerson.isEmpty()) {
            return order == null ? List.of() : List.copyOf(order);
        }
        List<Long> met = new ArrayList<>();
        List<Long> unmet = new ArrayList<>();
        for (Long id : order) {
            if (id == null) {
                continue;
            }
            CoursePlanCandidate candidate = candidatesByPerson.get(id);
            if (candidate != null && candidate.prerequisitesMet()) {
                met.add(id);
            } else {
                unmet.add(id);
            }
        }
        List<Long> result = new ArrayList<>(met.size() + unmet.size());
        result.addAll(met);
        result.addAll(unmet);
        return result;
    }
}
