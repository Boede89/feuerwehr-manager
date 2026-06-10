package de.feuerwehr.manager.berichte;

import java.util.List;

public record VehicleEquipmentView(
        long vehicleId,
        String vehicleName,
        List<EquipmentItemView> equipment) {

    public record EquipmentItemView(long id, String name, Long categoryId, String categoryName) {}
}
