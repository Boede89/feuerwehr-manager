package de.feuerwehr.manager.berichte;

import java.util.List;

public record CrewAssignment(
        long vehicleId,
        List<Long> personIds,
        Long einheitsfuehrerPersonId,
        Long maschinistPersonId,
        List<Long> paPersonIds,
        Boolean involvedInIncident,
        Boolean manuallyInvolvedInIncident,
        List<Long> csaPersonIds) {

    public CrewAssignment(long vehicleId, List<Long> personIds) {
        this(vehicleId, personIds, null, null, null, null, null, null);
    }

    /** Kompatibilität: ohne CSA-IDs. */
    public CrewAssignment(
            long vehicleId,
            List<Long> personIds,
            Long einheitsfuehrerPersonId,
            Long maschinistPersonId,
            List<Long> paPersonIds,
            Boolean involvedInIncident,
            Boolean manuallyInvolvedInIncident) {
        this(
                vehicleId,
                personIds,
                einheitsfuehrerPersonId,
                maschinistPersonId,
                paPersonIds,
                involvedInIncident,
                manuallyInvolvedInIncident,
                null);
    }

    public boolean isInvolvedInIncident() {
        return Boolean.TRUE.equals(involvedInIncident);
    }
}
