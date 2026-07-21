package de.feuerwehr.manager.reservierungen;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleReservationRepository extends JpaRepository<VehicleReservation, Long> {

    List<VehicleReservation> findByUnitIdOrderByStartAtDesc(long unitId);

    List<VehicleReservation> findByUnitIdAndStatusOrderByStartAtAsc(long unitId, ReservationStatus status);

    List<VehicleReservation> findByUnitIdAndRequesterUserIdOrderByStartAtDesc(long unitId, long requesterUserId);

    @Query("""
            SELECT r FROM VehicleReservation r
            WHERE r.vehicle.id = :vehicleId
              AND r.status = de.feuerwehr.manager.reservierungen.ReservationStatus.APPROVED
              AND r.startAt < :endAt
              AND r.endAt > :startAt
              AND (:excludeId IS NULL OR r.id <> :excludeId)
            """)
    List<VehicleReservation> findApprovedConflicts(
            @Param("vehicleId") long vehicleId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("excludeId") Long excludeId);
}
