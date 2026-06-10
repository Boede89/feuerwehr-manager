package de.feuerwehr.manager.berichte;

import java.util.List;

public record CrewAssignment(
        long vehicleId,
        List<Long> personIds,
        Long einheitsfuehrerPersonId,
        Long maschinistPersonId,
        List<Long> paPersonIds,
        Boolean involvedInIncident,
        Boolean manuallyInvolvedInIncident) {

    public CrewAssignment(long vehicleId, List<Long> personIds) {
        this(vehicleId, personIds, null, null, null, null, null);
    }

    public boolean resolvesInvolvedInIncident() {
        if (Boolean.TRUE.equals(involvedInIncident)) {
            return true;
        }
        return personIds != null && !personIds.isEmpty();
    }
}
