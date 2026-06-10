package de.feuerwehr.manager.technik;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitEquipmentCategoryRepository extends JpaRepository<UnitEquipmentCategory, Long> {

    List<UnitEquipmentCategory> findByUnitIdOrderBySortOrderAscNameAsc(long unitId);

    Optional<UnitEquipmentCategory> findByIdAndUnitId(long id, long unitId);

    boolean existsByUnitIdAndNameIgnoreCase(long unitId, String name);
}
