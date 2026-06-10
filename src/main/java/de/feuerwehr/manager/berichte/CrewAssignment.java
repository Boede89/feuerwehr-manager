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

    public boolean isInvolvedInIncident() {
        return Boolean.TRUE.equals(involvedInIncident);
    }
}
