package de.feuerwehr.manager.technik;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleEquipmentRepository extends JpaRepository<VehicleEquipment, Long> {

    List<VehicleEquipment> findByVehicleIdOrderBySortOrderAscNameAsc(long vehicleId);

    @Query("""
            SELECT e FROM VehicleEquipment e
            LEFT JOIN FETCH e.category
            WHERE e.vehicle.id = :vehicleId
            ORDER BY e.sortOrder ASC, e.name ASC
            """)
    List<VehicleEquipment> findByVehicleIdWithCategoryOrderBySortOrderAscNameAsc(@Param("vehicleId") long vehicleId);

    long countByVehicleId(long vehicleId);

    boolean existsByVehicleIdAndNameIgnoreCase(long vehicleId, String name);
}
