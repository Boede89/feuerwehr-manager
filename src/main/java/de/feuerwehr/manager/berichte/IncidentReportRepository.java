package de.feuerwehr.manager.berichte;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {

    @Query("""
            SELECT r FROM IncidentReport r
            LEFT JOIN FETCH r.createdByUser
            WHERE r.unit.id = :unitId
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            ORDER BY r.incidentDate DESC, r.id DESC
            """)
    List<IncidentReport> findByUnitIdOrderByDateDesc(
            @Param("unitId") long unitId, @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT r FROM IncidentReport r
            LEFT JOIN FETCH r.createdByUser
            WHERE r.unit.id = :unitId
              AND r.incidentDate >= :yearStart
              AND r.incidentDate < :yearEnd
              AND (:stichwort = '' OR r.stichwort = :stichwort)
              AND (:status IS NULL OR r.status = :status)
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            ORDER BY r.incidentDate DESC, r.id DESC
            """)
    List<IncidentReport> findFilteredByUnit(
            @Param("unitId") long unitId,
            @Param("yearStart") LocalDate yearStart,
            @Param("yearEnd") LocalDate yearEnd,
            @Param("stichwort") String stichwort,
            @Param("status") IncidentReportStatus status,
            @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT r FROM IncidentReport r
            LEFT JOIN FETCH r.createdByUser
            WHERE r.unit.id = :unitId
              AND r.incidentDate >= :yearStart
              AND r.incidentDate < :yearEnd
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            ORDER BY r.incidentDate DESC, r.id DESC
            """)
    List<IncidentReport> findByUnitIdAndYear(
            @Param("unitId") long unitId,
            @Param("yearStart") LocalDate yearStart,
            @Param("yearEnd") LocalDate yearEnd,
            @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT r FROM IncidentReport r
            LEFT JOIN FETCH r.createdByUser
            WHERE r.unit.id = :unitId
              AND r.incidentDate >= :from
              AND r.incidentDate <= :to
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            ORDER BY r.incidentDate DESC, r.id DESC
            """)
    List<IncidentReport> findByUnitIdAndDateRange(
            @Param("unitId") long unitId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT r FROM IncidentReport r
            LEFT JOIN FETCH r.commanderPerson
            WHERE r.id = :id AND r.unit.id = :unitId
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            """)
    Optional<IncidentReport> findByIdAndUnitId(
            @Param("id") long id,
            @Param("unitId") long unitId,
            @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT r.incidentNumber FROM IncidentReport r
            WHERE r.unit.id = :unitId
              AND r.incidentNumber IS NOT NULL
              AND r.incidentNumber LIKE CONCAT(:yearPrefix, '%')
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            """)
    List<String> findIncidentNumbersForYear(
            @Param("unitId") long unitId,
            @Param("yearPrefix") String yearPrefix,
            @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT r FROM IncidentReport r
            WHERE r.unit.id = :unitId
              AND r.incidentNumber IS NOT NULL
              AND r.incidentNumber LIKE CONCAT(:yearPrefix, '%')
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            """)
    List<IncidentReport> findReportsWithIncidentNumberForYear(
            @Param("unitId") long unitId,
            @Param("yearPrefix") String yearPrefix,
            @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN TRUE ELSE FALSE END FROM IncidentReport r
            WHERE r.unit.id = :unitId
              AND r.incidentNumber = :incidentNumber
              AND (:excludeId IS NULL OR r.id <> :excludeId)
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            """)
    boolean existsIncidentNumber(
            @Param("unitId") long unitId,
            @Param("incidentNumber") String incidentNumber,
            @Param("excludeId") Long excludeId,
            @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT DISTINCT r.stichwort FROM IncidentReport r
            WHERE r.unit.id = :unitId AND r.stichwort IS NOT NULL AND r.stichwort <> ''
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            ORDER BY r.stichwort
            """)
    List<String> findDistinctStichworteByUnitId(
            @Param("unitId") long unitId, @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT r FROM IncidentReport r
            WHERE r.unit.id = :unitId AND r.diveraAlarmId = :diveraAlarmId
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            """)
    Optional<IncidentReport> findByUnitIdAndDiveraAlarmId(
            @Param("unitId") long unitId,
            @Param("diveraAlarmId") long diveraAlarmId,
            @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT r FROM IncidentReport r
            WHERE r.productionSourceId = :productionSourceId
            """)
    Optional<IncidentReport> findByProductionSourceId(@Param("productionSourceId") long productionSourceId);

    @Modifying
    @Query("DELETE FROM IncidentReport r WHERE r.testData = true")
    void deleteAllByTestDataTrue();
}
