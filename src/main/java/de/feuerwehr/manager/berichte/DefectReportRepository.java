package de.feuerwehr.manager.berichte;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DefectReportRepository extends JpaRepository<DefectReport, Long> {

    @Query("""
            SELECT r FROM DefectReport r
            LEFT JOIN FETCH r.createdByUser
            LEFT JOIN FETCH r.recordedPerson
            LEFT JOIN FETCH r.vehicle
            WHERE r.unit.id = :unitId
              AND r.aufgenommenAm >= :yearStart
              AND r.aufgenommenAm < :yearEnd
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            ORDER BY r.aufgenommenAm DESC, r.id DESC
            """)
    List<DefectReport> findByUnitIdAndYear(
            @Param("unitId") long unitId,
            @Param("yearStart") LocalDate yearStart,
            @Param("yearEnd") LocalDate yearEnd,
            @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT r FROM DefectReport r
            LEFT JOIN FETCH r.createdByUser
            LEFT JOIN FETCH r.recordedPerson
            LEFT JOIN FETCH r.vehicle
            LEFT JOIN FETCH r.unit
            WHERE r.id = :id AND r.unit.id = :unitId
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            """)
    Optional<DefectReport> findByIdAndUnitId(
            @Param("id") long id,
            @Param("unitId") long unitId,
            @Param("includeTestReports") boolean includeTestReports);

    @Modifying
    @Query("DELETE FROM DefectReport r WHERE r.testData = true")
    void deleteAllByTestDataTrue();
}
