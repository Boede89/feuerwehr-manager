package de.feuerwehr.manager.divera;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TestDiveraAlarmRepository extends JpaRepository<TestDiveraAlarm, Long> {

    List<TestDiveraAlarm> findByUnitIdAndClosedFalseOrderByCreatedAtDesc(long unitId);

    Optional<TestDiveraAlarm> findByUnitIdAndAlarmIdAndClosedFalse(long unitId, long alarmId);

    List<TestDiveraAlarm> findByUnitIdOrderByCreatedAtDesc(long unitId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM TestDiveraAlarm")
    int deleteAllAlarms();
}
