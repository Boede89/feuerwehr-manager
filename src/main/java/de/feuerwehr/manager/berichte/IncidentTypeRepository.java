package de.feuerwehr.manager.berichte;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentTypeRepository extends JpaRepository<IncidentType, Long> {

    List<IncidentType> findByActiveTrueOrderByCategoryAscSortOrderAscLabelAsc();
}
