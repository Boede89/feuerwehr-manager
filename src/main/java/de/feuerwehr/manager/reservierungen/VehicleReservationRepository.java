package de.feuerwehr.manager.reservierungen;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleReservationRepository extends JpaRepository<VehicleReservation, Long> {

    List<VehicleReservation> findByUnitIdOrderByStartAtDesc(long unitId);

    List<VehicleReservation> findByUnitIdAndStatusOrderByStartAtAsc(long unitId, ReservationStatus status);

    List<VehicleReservation> findByUnitIdAndRequesterUserIdOrderByStartAtDesc(long unitId, long requesterUserId);

    List<VehicleReservation> findByVehicleIdInAndStatus(Collection<Long> vehicleIds, ReservationStatus status);
}
