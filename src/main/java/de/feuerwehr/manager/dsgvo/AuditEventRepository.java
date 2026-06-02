package de.feuerwehr.manager.dsgvo;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AuditEvent e WHERE e.occurredAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
