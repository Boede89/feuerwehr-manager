package de.feuerwehr.manager.technik;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleChecklistEntryRepository extends JpaRepository<VehicleChecklistEntry, Long> {

    List<VehicleChecklistEntry> findByChecklistIdOrderByIdAsc(Long checklistId);

    void deleteByChecklistId(Long checklistId);
}
