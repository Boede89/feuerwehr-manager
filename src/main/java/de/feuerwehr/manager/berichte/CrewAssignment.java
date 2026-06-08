package de.feuerwehr.manager.berichte;

import java.util.List;

public record CrewAssignment(
        long vehicleId,
        List<Long> personIds,
        Long einheitsfuehrerPersonId,
        Long maschinistPersonId,
        List<Long> paPersonIds) {

    public CrewAssignment(long vehicleId, List<Long> personIds) {
        this(vehicleId, personIds, null, null, null);
    }
}
