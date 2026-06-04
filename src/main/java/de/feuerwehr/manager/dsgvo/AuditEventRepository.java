package de.feuerwehr.manager.dsgvo;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AuditEvent e WHERE e.occurredAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
