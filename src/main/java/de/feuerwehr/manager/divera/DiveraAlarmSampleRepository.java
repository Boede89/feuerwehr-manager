package de.feuerwehr.manager.divera;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DiveraAlarmSampleRepository extends JpaRepository<DiveraAlarmSample, Long> {

    Optional<DiveraAlarmSample> findByUnitIdAndAlarmId(long unitId, long alarmId);

    Optional<DiveraAlarmSample> findByIdAndUnitId(long id, long unitId);

    List<DiveraAlarmSample> findByUnitIdOrderByCapturedAtDesc(long unitId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM DiveraAlarmSample")
    int deleteAllSamples();
}
