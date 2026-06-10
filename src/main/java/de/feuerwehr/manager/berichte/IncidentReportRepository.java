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
            LEFT JOIN FETCH r.commanderPerson
            WHERE r.id = :id AND r.unit.id = :unitId
            """)
    Optional<IncidentReport> findByIdAndUnitId(@Param("id") long id, @Param("unitId") long unitId);

    @Query("""
            SELECT r.incidentNumber FROM IncidentReport r
            WHERE r.unit.id = :unitId
              AND r.incidentNumber IS NOT NULL
              AND r.incidentNumber LIKE CONCAT(:yearPrefix, '%')
            """)
    List<String> findIncidentNumbersForYear(@Param("unitId") long unitId, @Param("yearPrefix") String yearPrefix);

    @Query("""
            SELECT DISTINCT r.stichwort FROM IncidentReport r
            WHERE r.unit.id = :unitId AND r.stichwort IS NOT NULL AND r.stichwort <> ''
            ORDER BY r.stichwort
            """)
    List<String> findDistinctStichworteByUnitId(@Param("unitId") long unitId);

    Optional<IncidentReport> findByUnitIdAndDiveraAlarmId(long unitId, long diveraAlarmId);
}
