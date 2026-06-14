package de.feuerwehr.manager.berichte;

import java.util.List;
import java.util.Map;

public record GwmVehicleData(
        long vehicleId,
        Long maschinistPersonId,
        Long einheitsfuehrerPersonId,
        List<Long> equipmentIds,
        List<Long> defectiveEquipmentIds,
        Map<Long, String> defectiveMangelByEquipmentId,
        String defectiveFreitext,
        String defectiveFreitextMangel) {

    public GwmVehicleData(long vehicleId, List<Long> equipmentIds) {
        this(vehicleId, null, null, equipmentIds, List.of(), Map.of(), null, null);
    }
}
