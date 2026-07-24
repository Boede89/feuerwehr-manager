package de.feuerwehr.manager.leitstellen;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeitstellenMailImportRepository extends JpaRepository<LeitstellenMailImport, Long> {

    boolean existsByUnitIdAndAttachmentSha256(long unitId, String attachmentSha256);

    boolean existsByUnitIdAndMessageIdAndAttachmentName(long unitId, String messageId, String attachmentName);

    boolean existsByIncidentReportIdAndKind(long incidentReportId, LeitstellenMailKind kind);

    java.util.Optional<LeitstellenMailImport> findByIncidentReportIdAndKind(
            long incidentReportId, LeitstellenMailKind kind);

    java.util.List<LeitstellenMailImport> findByUnitId(long unitId);
}
