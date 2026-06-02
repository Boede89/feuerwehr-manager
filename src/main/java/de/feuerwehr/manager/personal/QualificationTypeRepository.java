package de.feuerwehr.manager.personal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QualificationTypeRepository extends JpaRepository<QualificationType, Long> {

    List<QualificationType> findByUnitIdAndTestDataAndActiveTrueOrderBySortOrderAscNameAsc(
            long unitId, boolean testData);

    List<QualificationType> findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(long unitId, boolean testData);

    @Query("SELECT q FROM QualificationType q WHERE q.productionSourceId = :sourceId")
    Optional<QualificationType> findShadowByProductionSourceId(@Param("sourceId") long sourceId);

    @Modifying
    @Query("DELETE FROM QualificationType q WHERE q.testData = true")
    void deleteAllByTestDataTrue();
}
