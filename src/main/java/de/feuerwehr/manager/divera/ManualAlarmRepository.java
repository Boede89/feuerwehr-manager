package de.feuerwehr.manager.divera;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManualAlarmRepository extends JpaRepository<ManualAlarm, Long> {

    List<ManualAlarm> findByUnitIdAndClosedFalseOrderByCreatedAtDesc(long unitId);

    List<ManualAlarm> findByUnitIdAndStartedFalseAndClosedFalseOrderByCreatedAtDesc(long unitId);

    List<ManualAlarm> findByUnitIdAndStartedTrueAndClosedFalseOrderByStartedAtDesc(long unitId);

    Optional<ManualAlarm> findByUnitIdAndAlarmId(long unitId, long alarmId);

    Optional<ManualAlarm> findByIdAndUnitId(long id, long unitId);

    @Query("SELECT m FROM ManualAlarm m JOIN FETCH m.unit WHERE m.id = :id AND m.unit.id = :unitId")
    Optional<ManualAlarm> findByIdAndUnitIdWithUnit(@Param("id") long id, @Param("unitId") long unitId);

    @Query("""
            SELECT m.alarmNumber FROM ManualAlarm m
            WHERE m.unit.id = :unitId
              AND m.alarmNumber IS NOT NULL
              AND m.alarmNumber LIKE CONCAT(:yearPrefix, '%')
            """)
    List<String> findAlarmNumbersForYear(@Param("unitId") long unitId, @Param("yearPrefix") String yearPrefix);
}
