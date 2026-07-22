package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.termine.TermineCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceReportRepository extends JpaRepository<AttendanceReport, Long> {

    @Query("""
            SELECT r FROM AttendanceReport r
            LEFT JOIN FETCH r.createdByUser
            LEFT JOIN FETCH r.unitTermin
            WHERE r.unit.id = :unitId
              AND r.eventDate >= :yearStart
              AND r.eventDate < :yearEnd
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            ORDER BY r.eventDate DESC, r.id DESC
            """)
    List<AttendanceReport> findByUnitIdAndYear(
            @Param("unitId") long unitId,
            @Param("yearStart") LocalDate yearStart,
            @Param("yearEnd") LocalDate yearEnd,
            @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT r FROM AttendanceReport r
            LEFT JOIN FETCH r.createdByUser
            LEFT JOIN FETCH r.unitTermin
            WHERE r.id = :id AND r.unit.id = :unitId
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            """)
    Optional<AttendanceReport> findByIdAndUnitId(
            @Param("id") long id,
            @Param("unitId") long unitId,
            @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT r FROM AttendanceReport r
            WHERE r.unit.id = :unitId AND r.unitTermin.id = :terminId
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            """)
    Optional<AttendanceReport> findByUnitIdAndUnitTerminId(
            @Param("unitId") long unitId,
            @Param("terminId") long terminId,
            @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT r.reportNumber FROM AttendanceReport r
            WHERE r.unit.id = :unitId
              AND r.reportNumber IS NOT NULL
              AND r.reportNumber LIKE CONCAT(:yearPrefix, '%')
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            """)
    List<String> findReportNumbersForYear(
            @Param("unitId") long unitId,
            @Param("yearPrefix") String yearPrefix,
            @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT DISTINCT r.title FROM AttendanceReport r
            WHERE r.unit.id = :unitId
              AND r.title IS NOT NULL AND TRIM(r.title) <> ''
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            ORDER BY r.title ASC
            """)
    List<String> findDistinctTitlesByUnit(
            @Param("unitId") long unitId, @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT DISTINCT r.title FROM AttendanceReport r
            WHERE r.unit.id = :unitId
              AND r.title IS NOT NULL AND TRIM(r.title) <> ''
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
              AND (
                    r.terminCategory = :category
                    OR (r.terminCategory IS NULL AND :includeNullCategory = TRUE)
              )
            ORDER BY r.title ASC
            """)
    List<String> findDistinctTitlesByUnitAndCategory(
            @Param("unitId") long unitId,
            @Param("category") TermineCategory category,
            @Param("includeNullCategory") boolean includeNullCategory,
            @Param("includeTestReports") boolean includeTestReports);
}
