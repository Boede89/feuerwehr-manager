package de.feuerwehr.manager.berichte;

import java.util.List;

public record DeployedEquipmentAssignment(long vehicleId, List<Long> equipmentIds) {}
