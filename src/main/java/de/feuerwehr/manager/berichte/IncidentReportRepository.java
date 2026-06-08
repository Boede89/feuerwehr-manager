package de.feuerwehr.manager.berichte;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {

    @Query("""
            SELECT r FROM IncidentReport r
            WHERE r.unit.id = :unitId
            ORDER BY r.incidentDate DESC, r.id DESC
            """)
    List<IncidentReport> findByUnitIdOrderByDateDesc(@Param("unitId") long unitId);

    @Query("""
            SELECT r FROM IncidentReport r
            WHERE r.id = :id AND r.unit.id = :unitId
            """)
    Optional<IncidentReport> findByIdAndUnitId(@Param("id") long id, @Param("unitId") long unitId);

    @Query("""
            SELECT MAX(r.incidentNumber) FROM IncidentReport r
            WHERE r.unit.id = :unitId AND r.incidentNumber LIKE CONCAT(:datePrefix, '%')
            """)
    Optional<String> findMaxIncidentNumberForDate(@Param("unitId") long unitId, @Param("datePrefix") String datePrefix);
}
