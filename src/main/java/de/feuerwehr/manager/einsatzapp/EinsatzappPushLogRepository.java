package de.feuerwehr.manager.einsatzapp;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EinsatzappPushLogRepository extends JpaRepository<EinsatzappPushLog, Long> {

    List<EinsatzappPushLog> findTop10ByUnitIdOrderByCreatedAtDesc(long unitId);
}
