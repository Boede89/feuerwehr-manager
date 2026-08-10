package de.feuerwehr.manager.reservierungen;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleReservationRepository extends JpaRepository<VehicleReservation, Long> {

    List<VehicleReservation> findByUnitIdOrderByStartAtDesc(long unitId);

    List<VehicleReservation> findByUnitIdAndStatusOrderByStartAtAsc(long unitId, ReservationStatus status);

    List<VehicleReservation> findByUnitIdAndRequesterUserIdOrderByStartAtDesc(long unitId, long requesterUserId);

    List<VehicleReservation> findByTestDataTrue();

    List<VehicleReservation> findByDiveraEventIdAndStatusAndIdNot(
            Long diveraEventId, ReservationStatus status, Long excludeId);

    /**
     * Genehmigte Fahrzeugreservierungen mit gleichem Zeitraum und Grund (für gemeinsamen Kalendertermin).
     */
    @Query("""
            SELECT r FROM VehicleReservation r
            WHERE r.unit.id = :unitId
              AND r.status = :status
              AND r.startAt = :startAt
              AND r.endAt = :endAt
              AND LOWER(TRIM(r.reason)) = LOWER(TRIM(:reason))
              AND r.id <> :excludeId
            """)
    List<VehicleReservation> findApprovedSlotSiblings(
            @Param("unitId") long unitId,
            @Param("status") ReservationStatus status,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("reason") String reason,
            @Param("excludeId") long excludeId);

    @Query("""
            SELECT DISTINCT r FROM VehicleReservation r
            LEFT JOIN r.vehicles v
            WHERE r.status = :status
              AND (r.vehicle.id IN :vehicleIds OR v.id IN :vehicleIds)
            """)
    List<VehicleReservation> findByStatusAndAnyVehicleIdIn(
            @Param("status") ReservationStatus status, @Param("vehicleIds") Collection<Long> vehicleIds);

    @Query("""
            SELECT DISTINCT r FROM VehicleReservation r
            LEFT JOIN r.vehicles v
            WHERE r.status IN :statuses
              AND (r.vehicle.id IN :vehicleIds OR v.id IN :vehicleIds)
            """)
    List<VehicleReservation> findByStatusInAndAnyVehicleIdIn(
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("vehicleIds") Collection<Long> vehicleIds);
}
