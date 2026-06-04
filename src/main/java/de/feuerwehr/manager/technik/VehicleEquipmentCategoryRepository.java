package de.feuerwehr.manager.technik;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleEquipmentCategoryRepository extends JpaRepository<VehicleEquipmentCategory, Long> {

    List<VehicleEquipmentCategory> findByVehicleIdOrderBySortOrderAscNameAsc(long vehicleId);
}
