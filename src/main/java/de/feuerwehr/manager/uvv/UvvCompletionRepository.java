package de.feuerwehr.manager.uvv;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UvvCompletionRepository extends JpaRepository<UvvCompletion, Long> {

    Optional<UvvCompletion> findByPersonIdAndCampaignId(long personId, long campaignId);

    boolean existsByPersonIdAndCampaignId(long personId, long campaignId);

    @Query(
            """
            SELECT c FROM UvvCompletion c
            LEFT JOIN FETCH c.campaign
            LEFT JOIN FETCH c.recordedBy
            WHERE c.person.id = :personId
            ORDER BY c.completedOn DESC, c.id DESC
            """)
    List<UvvCompletion> findByPersonIdOrderByCompletedOnDesc(@Param("personId") long personId);

    @Query(
            """
            SELECT c FROM UvvCompletion c
            WHERE c.person.id IN :personIds
            """)
    List<UvvCompletion> findByPersonIdIn(@Param("personIds") Collection<Long> personIds);

    @Query(
            """
            SELECT c FROM UvvCompletion c
            WHERE c.campaign.id = :campaignId
            """)
    List<UvvCompletion> findByCampaignId(@Param("campaignId") long campaignId);

    Optional<UvvCompletion> findFirstByPersonIdOrderByCompletedOnDescIdDesc(long personId);
}
