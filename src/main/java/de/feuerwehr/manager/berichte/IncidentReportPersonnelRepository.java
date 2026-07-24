package de.feuerwehr.manager.berichte;

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

    @Modifying
    @Query("DELETE FROM IncidentReportPersonnel p WHERE p.incidentReport.id = :reportId")
    void deleteByIncidentReportId(@Param("reportId") long reportId);
}
