package de.feuerwehr.manager.berichte;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentReportVehicleRepository extends JpaRepository<IncidentReportVehicle, Long> {

    @Query("""
            SELECT v FROM IncidentReportVehicle v
            LEFT JOIN FETCH v.vehicle
            WHERE v.incidentReport.id = :reportId
            ORDER BY v.vehicleName
            """)
    List<IncidentReportVehicle> findByIncidentReportId(@Param("reportId") long reportId);

    @Query("""
            SELECT v FROM IncidentReportVehicle v
            WHERE v.incidentReport.id = :reportId AND v.vehicle.id = :vehicleId
            """)
    Optional<IncidentReportVehicle> findByIncidentReportIdAndVehicleId(
            @Param("reportId") long reportId, @Param("vehicleId") long vehicleId);

    @Modifying
    @Query("DELETE FROM IncidentReportVehicle v WHERE v.incidentReport.id = :reportId")
    void deleteByIncidentReportId(@Param("reportId") long reportId);
}
