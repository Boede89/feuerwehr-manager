package de.feuerwehr.manager.technik;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleChecklistItemRepository extends JpaRepository<VehicleChecklistItem, Long> {

    List<VehicleChecklistItem> findByTemplateIdOrderByPositionAscIdAsc(Long templateId);
}
