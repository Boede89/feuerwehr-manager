package de.feuerwehr.manager.technik;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleEquipmentCategoryRepository extends JpaRepository<VehicleEquipmentCategory, Long> {

    List<VehicleEquipmentCategory> findByVehicleIdOrderBySortOrderAscNameAsc(long vehicleId);

    Optional<VehicleEquipmentCategory> findByIdAndVehicleId(long id, long vehicleId);

    boolean existsByVehicleIdAndNameIgnoreCase(long vehicleId, String name);
}
