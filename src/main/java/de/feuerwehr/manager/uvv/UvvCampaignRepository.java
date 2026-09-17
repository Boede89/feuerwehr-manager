package de.feuerwehr.manager.uvv;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UvvCampaignRepository extends JpaRepository<UvvCampaign, Long> {

    List<UvvCampaign> findByUnitIdOrderByEventDateDescIdDesc(long unitId);

    List<UvvCampaign> findByUnitIdAndStatusOrderByEventDateDescIdDesc(long unitId, UvvCampaignStatus status);

    Optional<UvvCampaign> findByIdAndUnitId(long id, long unitId);

    @Query(
            """
            SELECT c FROM UvvCampaign c
            WHERE c.unit.id = :unitId AND c.status = de.feuerwehr.manager.uvv.UvvCampaignStatus.OPEN
            ORDER BY c.eventDate DESC, c.id DESC
            """)
    List<UvvCampaign> findOpenByUnitId(@Param("unitId") long unitId);
}
