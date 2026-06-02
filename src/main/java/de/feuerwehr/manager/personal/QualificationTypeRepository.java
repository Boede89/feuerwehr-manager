package de.feuerwehr.manager.personal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface QualificationTypeRepository extends JpaRepository<QualificationType, Long> {

    List<QualificationType> findByUnitIdAndTestDataAndActiveTrueOrderBySortOrderAscNameAsc(
            long unitId, boolean testData);

    List<QualificationType> findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(long unitId, boolean testData);

    @Modifying
    @Query("DELETE FROM QualificationType q WHERE q.testData = true")
    void deleteAllByTestDataTrue();
}
