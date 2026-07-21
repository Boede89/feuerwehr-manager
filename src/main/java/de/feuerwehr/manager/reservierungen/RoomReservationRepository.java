package de.feuerwehr.manager.reservierungen;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomReservationRepository extends JpaRepository<RoomReservation, Long> {

    List<RoomReservation> findByUnitIdOrderByStartAtDesc(long unitId);

    List<RoomReservation> findByUnitIdAndStatusOrderByStartAtAsc(long unitId, ReservationStatus status);

    List<RoomReservation> findByUnitIdAndRequesterUserIdOrderByStartAtDesc(long unitId, long requesterUserId);

    @Query("""
            SELECT r FROM RoomReservation r
            WHERE r.room.id = :roomId
              AND r.status = de.feuerwehr.manager.reservierungen.ReservationStatus.APPROVED
              AND r.startAt < :endAt
              AND r.endAt > :startAt
              AND (:excludeId IS NULL OR r.id <> :excludeId)
            """)
    List<RoomReservation> findApprovedConflicts(
            @Param("roomId") long roomId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("excludeId") Long excludeId);
}
