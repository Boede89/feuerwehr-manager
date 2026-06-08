package de.feuerwehr.manager.divera;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitDiveraStatusIdRepository extends JpaRepository<UnitDiveraStatusId, Long> {

    List<UnitDiveraStatusId> findByUnitIdOrderBySortOrderAscLabelAsc(long unitId);

    boolean existsByUnitIdAndStatusId(long unitId, String statusId);
}
