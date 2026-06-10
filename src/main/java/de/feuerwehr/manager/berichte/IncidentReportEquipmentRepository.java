package de.feuerwehr.manager.berichte;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentReportEquipmentRepository extends JpaRepository<IncidentReportEquipment, Long> {

    @Query("""
            SELECT e FROM IncidentReportEquipment e
            JOIN FETCH e.vehicle
            WHERE e.incidentReport.id = :reportId
            ORDER BY e.vehicle.name ASC, e.equipmentName ASC
            """)
    List<IncidentReportEquipment> findByIncidentReportId(@Param("reportId") long reportId);

    @Modifying
    @Query("DELETE FROM IncidentReportEquipment e WHERE e.incidentReport.id = :reportId")
    void deleteByIncidentReportId(@Param("reportId") long reportId);
}
