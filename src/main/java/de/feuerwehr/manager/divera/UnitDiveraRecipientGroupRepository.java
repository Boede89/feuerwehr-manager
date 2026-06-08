package de.feuerwehr.manager.divera;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitDiveraRecipientGroupRepository extends JpaRepository<UnitDiveraRecipientGroup, Long> {

    List<UnitDiveraRecipientGroup> findByUnitIdOrderBySortOrderAscLabelAsc(long unitId);

    boolean existsByUnitIdAndGroupId(long unitId, String groupId);

    boolean existsByUnitIdAndGroupIdIsNullAndLabel(long unitId, String label);
}
