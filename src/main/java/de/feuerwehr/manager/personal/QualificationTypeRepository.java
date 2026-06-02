package de.feuerwehr.manager.personal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QualificationTypeRepository extends JpaRepository<QualificationType, Long> {

    List<QualificationType> findByUnitIdAndActiveTrueOrderBySortOrderAscNameAsc(long unitId);

    List<QualificationType> findByUnitIdOrderBySortOrderAscNameAsc(long unitId);
}
