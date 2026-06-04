package de.feuerwehr.manager.technik;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleChecklistTemplateRepository extends JpaRepository<VehicleChecklistTemplate, Long> {

    List<VehicleChecklistTemplate> findByVehicleIdOrderByNameAsc(Long vehicleId);

    Optional<VehicleChecklistTemplate> findByIdAndVehicleId(Long id, Long vehicleId);
}
