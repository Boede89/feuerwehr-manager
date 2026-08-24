package de.feuerwehr.manager.berichte;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentReportPersonnelRepository extends JpaRepository<IncidentReportPersonnel, Long> {

    @Query("""
            SELECT p FROM IncidentReportPersonnel p
            LEFT JOIN FETCH p.person pers
            LEFT JOIN FETCH pers.unit
            LEFT JOIN FETCH pers.qualificationType
            LEFT JOIN FETCH p.foreignUnit
            LEFT JOIN FETCH p.incidentReportVehicle irv
            LEFT JOIN FETCH irv.vehicle
            WHERE p.incidentReport.id = :reportId
            ORDER BY p.displayName
            """)
    List<IncidentReportPersonnel> findByIncidentReportId(@Param("reportId") long reportId);

    @Query("""
            SELECT p FROM IncidentReportPersonnel p
            JOIN FETCH p.incidentReport
            LEFT JOIN FETCH p.person pers
            LEFT JOIN FETCH pers.qualificationType
            WHERE p.incidentReport.id IN :reportIds
            """)
    List<IncidentReportPersonnel> findByIncidentReportIdIn(@Param("reportIds") Collection<Long> reportIds);

    @Query("""
            SELECT DISTINCT r FROM IncidentReportPersonnel p
            JOIN p.incidentReport r
            WHERE p.person.id = :personId
              AND p.usesPa = TRUE
              AND r.unit.id = :unitId
              AND r.incidentDate >= :yearStart
              AND r.incidentDate < :yearEnd
              AND r.status IN :statuses
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            ORDER BY r.incidentDate DESC, r.id DESC
            """)
    List<IncidentReport> findPaReportsByPersonAndYear(
            @Param("personId") long personId,
            @Param("unitId") long unitId,
            @Param("yearStart") LocalDate yearStart,
            @Param("yearEnd") LocalDate yearEnd,
            @Param("statuses") Collection<IncidentReportStatus> statuses,
            @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT DISTINCT YEAR(r.incidentDate) FROM IncidentReportPersonnel p
            JOIN p.incidentReport r
            WHERE p.person.id = :personId
              AND p.usesPa = TRUE
              AND r.unit.id = :unitId
              AND r.incidentDate IS NOT NULL
              AND r.status IN :statuses
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            ORDER BY YEAR(r.incidentDate) DESC
            """)
    List<Integer> findDistinctPaYearsByPerson(
            @Param("personId") long personId,
            @Param("unitId") long unitId,
            @Param("statuses") Collection<IncidentReportStatus> statuses,
            @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT p FROM IncidentReportPersonnel p
            JOIN FETCH p.incidentReport r
            WHERE p.person.id = :personId
              AND r.unit.id = :unitId
              AND r.status IN :statuses
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            """)
    List<IncidentReportPersonnel> findByPersonAndUnit(
            @Param("personId") long personId,
            @Param("unitId") long unitId,
            @Param("statuses") Collection<IncidentReportStatus> statuses,
            @Param("includeTestReports") boolean includeTestReports);

    @Modifying
    @Query("DELETE FROM IncidentReportPersonnel p WHERE p.incidentReport.id = :reportId")
    void deleteByIncidentReportId(@Param("reportId") long reportId);
}
