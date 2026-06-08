package de.feuerwehr.manager.berichte;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentReportPersonnelRepository extends JpaRepository<IncidentReportPersonnel, Long> {

    @Query("""
            SELECT p FROM IncidentReportPersonnel p
            LEFT JOIN FETCH p.person
            LEFT JOIN FETCH p.incidentReportVehicle irv
            LEFT JOIN FETCH irv.vehicle
            WHERE p.incidentReport.id = :reportId
            ORDER BY p.displayName
            """)
    List<IncidentReportPersonnel> findByIncidentReportId(@Param("reportId") long reportId);

    @Modifying
    @Query("DELETE FROM IncidentReportPersonnel p WHERE p.incidentReport.id = :reportId")
    void deleteByIncidentReportId(@Param("reportId") long reportId);
}
