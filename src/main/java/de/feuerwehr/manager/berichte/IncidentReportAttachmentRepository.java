package de.feuerwehr.manager.berichte;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentReportAttachmentRepository extends JpaRepository<IncidentReportAttachment, Long> {

    List<IncidentReportAttachment> findByIncidentReportIdOrderByCreatedAtAsc(long incidentReportId);

    Optional<IncidentReportAttachment> findByIdAndIncidentReportId(long id, long incidentReportId);

    void deleteByIncidentReportId(long incidentReportId);
}
