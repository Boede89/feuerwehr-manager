package de.feuerwehr.manager.technik;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleChecklistRepository extends JpaRepository<VehicleChecklist, Long> {

    @Query(
            """
            SELECT c FROM VehicleChecklist c
            JOIN FETCH c.template t
            WHERE c.vehicle.id = :vehicleId
            ORDER BY c.filledAt DESC
            """)
    List<VehicleChecklist> findByVehicleIdWithTemplateOrderByFilledAtDesc(@Param("vehicleId") Long vehicleId);

    @Query(
            """
            SELECT c FROM VehicleChecklist c
            JOIN FETCH c.template t
            LEFT JOIN FETCH c.filledBy
            WHERE c.id = :id AND c.vehicle.id = :vehicleId
            """)
    Optional<VehicleChecklist> findByIdAndVehicleIdWithDetails(
            @Param("id") Long id, @Param("vehicleId") Long vehicleId);
}
