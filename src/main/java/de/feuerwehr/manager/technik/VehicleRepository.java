package de.feuerwehr.manager.technik;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(long unitId, boolean testData);

    Optional<Vehicle> findByIdAndUnitId(long id, long unitId);

    long countByUnitIdAndVehicleType(long unitId, String vehicleType);
}
