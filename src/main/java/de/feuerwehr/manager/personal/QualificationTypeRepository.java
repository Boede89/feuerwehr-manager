package de.feuerwehr.manager.personal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QualificationTypeRepository extends JpaRepository<QualificationType, Long> {

    @Query(
            """
            SELECT q FROM QualificationType q
            LEFT JOIN FETCH q.dienstgradRole
            WHERE q.unit.id = :unitId AND q.testData = :testData AND q.active = TRUE
            ORDER BY q.sortOrder ASC, q.name ASC
            """)
    List<QualificationType> findByUnitIdAndTestDataAndActiveTrueOrderBySortOrderAscNameAsc(
            @Param("unitId") long unitId, @Param("testData") boolean testData);

    @Query(
            """
            SELECT q FROM QualificationType q
            LEFT JOIN FETCH q.dienstgradRole
            WHERE q.unit.id = :unitId AND q.testData = :testData
            ORDER BY q.sortOrder ASC, q.name ASC
            """)
    List<QualificationType> findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(
            @Param("unitId") long unitId, @Param("testData") boolean testData);

    @Query(
            """
            SELECT q FROM QualificationType q
            LEFT JOIN FETCH q.dienstgradRole
            WHERE q.id = :id
            """)
    Optional<QualificationType> findByIdWithDienstgradRole(@Param("id") long id);

    @Query("SELECT q FROM QualificationType q WHERE q.productionSourceId = :sourceId")
    Optional<QualificationType> findShadowByProductionSourceId(@Param("sourceId") long sourceId);

    @Modifying
    @Query("DELETE FROM QualificationType q WHERE q.testData = true")
    void deleteAllByTestDataTrue();
}
