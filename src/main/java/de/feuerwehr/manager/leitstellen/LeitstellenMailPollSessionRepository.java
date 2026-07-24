package de.feuerwehr.manager.leitstellen;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeitstellenMailPollSessionRepository extends JpaRepository<LeitstellenMailPollSession, Long> {

    Optional<LeitstellenMailPollSession> findByIncidentReportId(long incidentReportId);

    @Query("""
            SELECT DISTINCT s.unit.id FROM LeitstellenMailPollSession s
            WHERE s.phase IN :activePhases
              AND s.nextPollAt <= :now
            """)
    List<Long> findUnitIdsDue(
            @Param("activePhases") List<LeitstellenPollPhase> activePhases, @Param("now") Instant now);

    @Query("""
            SELECT s FROM LeitstellenMailPollSession s
            WHERE s.unit.id = :unitId
              AND s.phase IN :activePhases
            """)
    List<LeitstellenMailPollSession> findActiveByUnitId(
            @Param("unitId") long unitId, @Param("activePhases") List<LeitstellenPollPhase> activePhases);
}
