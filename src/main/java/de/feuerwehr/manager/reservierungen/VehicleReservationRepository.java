package de.feuerwehr.manager.reservierungen;

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

    @Query("""
            SELECT DISTINCT r FROM VehicleReservation r
            LEFT JOIN r.vehicles v
            WHERE r.status = :status
              AND (r.vehicle.id IN :vehicleIds OR v.id IN :vehicleIds)
            """)
    List<VehicleReservation> findByStatusAndAnyVehicleIdIn(
            @Param("status") ReservationStatus status, @Param("vehicleIds") Collection<Long> vehicleIds);
}
