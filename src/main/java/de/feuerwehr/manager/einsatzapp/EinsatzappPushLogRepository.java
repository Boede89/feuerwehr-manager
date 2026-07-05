package de.feuerwehr.manager.einsatzapp;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EinsatzappPushLogRepository extends JpaRepository<EinsatzappPushLog, Long> {

    List<EinsatzappPushLog> findTop10ByUnitIdOrderByCreatedAtDesc(long unitId);

    Optional<EinsatzappPushLog> findTopByUnitIdAndDiveraAlarmIdOrderByCreatedAtDesc(long unitId, long diveraAlarmId);
}
