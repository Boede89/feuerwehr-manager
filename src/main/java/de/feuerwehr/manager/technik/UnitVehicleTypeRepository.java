package de.feuerwehr.manager.technik;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitVehicleTypeRepository extends JpaRepository<UnitVehicleType, Long> {

    List<UnitVehicleType> findByUnitIdOrderBySortOrderAscLabelAsc(long unitId);

    boolean existsByUnitIdAndTypeKeyIgnoreCase(long unitId, String typeKey);

    Optional<UnitVehicleType> findByIdAndUnitId(long id, long unitId);
}
