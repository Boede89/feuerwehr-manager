package de.feuerwehr.manager.divera;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualAlarmRepository extends JpaRepository<ManualAlarm, Long> {

    List<ManualAlarm> findByUnitIdAndClosedFalseOrderByCreatedAtDesc(long unitId);

    Optional<ManualAlarm> findByUnitIdAndAlarmId(long unitId, long alarmId);

    Optional<ManualAlarm> findByIdAndUnitId(long id, long unitId);
}
