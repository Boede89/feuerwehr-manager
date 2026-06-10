package de.feuerwehr.manager.berichte;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentReportChangeRepository extends JpaRepository<IncidentReportChange, Long> {

    @Query("""
            SELECT c FROM IncidentReportChange c
            LEFT JOIN FETCH c.fields
            WHERE c.incidentReport.id = :reportId
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    List<IncidentReportChange> findByReportIdWithFields(@Param("reportId") long reportId);
}
