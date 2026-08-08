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
        String defectiveFreitextMangel,
        List<CustomDeployedEquipment> customEquipment) {

    public GwmVehicleData {
        equipmentIds = equipmentIds != null ? equipmentIds : List.of();
        defectiveEquipmentIds = defectiveEquipmentIds != null ? defectiveEquipmentIds : List.of();
        defectiveMangelByEquipmentId =
                defectiveMangelByEquipmentId != null ? defectiveMangelByEquipmentId : Map.of();
        customEquipment = customEquipment != null ? customEquipment : List.of();
    }

    public GwmVehicleData(long vehicleId, List<Long> equipmentIds) {
        this(vehicleId, null, null, equipmentIds, List.of(), Map.of(), null, null, List.of());
    }

    public GwmVehicleData(
            long vehicleId,
            Long maschinistPersonId,
            Long einheitsfuehrerPersonId,
            List<Long> equipmentIds,
            List<Long> defectiveEquipmentIds,
            Map<Long, String> defectiveMangelByEquipmentId,
            String defectiveFreitext,
            String defectiveFreitextMangel) {
        this(
                vehicleId,
                maschinistPersonId,
                einheitsfuehrerPersonId,
                equipmentIds,
                defectiveEquipmentIds,
                defectiveMangelByEquipmentId,
                defectiveFreitext,
                defectiveFreitextMangel,
                List.of());
    }

    public boolean hasEquipment() {
        return (equipmentIds != null && !equipmentIds.isEmpty())
                || (customEquipment != null && !customEquipment.isEmpty());
    }
}
