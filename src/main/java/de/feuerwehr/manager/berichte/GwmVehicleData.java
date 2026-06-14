package de.feuerwehr.manager.berichte;

import java.util.List;

public record GwmVehicleData(
        long vehicleId,
        Long maschinistPersonId,
        Long einheitsfuehrerPersonId,
        List<Long> equipmentIds,
        List<Long> defectiveEquipmentIds,
        String defectiveFreitext,
        String defectiveMangel) {

    public GwmVehicleData(long vehicleId, List<Long> equipmentIds) {
        this(vehicleId, null, null, equipmentIds, List.of(), null, null);
    }
}
