package de.feuerwehr.manager.berichte;

import java.util.List;

public record DeployedEquipmentAssignment(
        long vehicleId, List<Long> equipmentIds, List<CustomDeployedEquipment> customEquipment) {

    public DeployedEquipmentAssignment(long vehicleId, List<Long> equipmentIds) {
        this(vehicleId, equipmentIds, List.of());
    }
}
