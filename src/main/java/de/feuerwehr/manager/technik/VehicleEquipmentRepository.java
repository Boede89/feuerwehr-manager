package de.feuerwehr.manager.technik;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleEquipmentRepository extends JpaRepository<VehicleEquipment, Long> {

    List<VehicleEquipment> findByVehicleIdOrderBySortOrderAscNameAsc(long vehicleId);
}
