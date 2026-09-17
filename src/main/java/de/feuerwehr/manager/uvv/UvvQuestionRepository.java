package de.feuerwehr.manager.uvv;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UvvQuestionRepository extends JpaRepository<UvvQuestion, Long> {

    List<UvvQuestion> findByCampaignIdOrderBySortOrderAscIdAsc(long campaignId);

    long countByCampaignId(long campaignId);

    void deleteByCampaignId(long campaignId);
}
