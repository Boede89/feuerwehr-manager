package de.feuerwehr.manager.berichte;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentMaintenanceReportRepository extends JpaRepository<EquipmentMaintenanceReport, Long> {

    @Query("""
            SELECT r FROM EquipmentMaintenanceReport r
            LEFT JOIN FETCH r.createdByUser
            LEFT JOIN FETCH r.leaderPerson
            WHERE r.unit.id = :unitId
              AND r.eventDate >= :yearStart
              AND r.eventDate < :yearEnd
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            ORDER BY r.eventDate DESC, r.id DESC
            """)
    List<EquipmentMaintenanceReport> findByUnitIdAndYear(
            @Param("unitId") long unitId,
            @Param("yearStart") LocalDate yearStart,
            @Param("yearEnd") LocalDate yearEnd,
            @Param("includeTestReports") boolean includeTestReports);

    @Query("""
            SELECT r FROM EquipmentMaintenanceReport r
            LEFT JOIN FETCH r.createdByUser
            LEFT JOIN FETCH r.leaderPerson
            LEFT JOIN FETCH r.unit
            WHERE r.id = :id AND r.unit.id = :unitId
              AND (r.testData = FALSE OR :includeTestReports = TRUE)
            """)
    Optional<EquipmentMaintenanceReport> findByIdAndUnitId(
            @Param("id") long id,
            @Param("unitId") long unitId,
            @Param("includeTestReports") boolean includeTestReports);

    @Modifying
    @Query("DELETE FROM EquipmentMaintenanceReport r WHERE r.testData = true")
    void deleteAllByTestDataTrue();
}
