package de.feuerwehr.manager.reservierungen;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomReservationRepository extends JpaRepository<RoomReservation, Long> {

    List<RoomReservation> findByUnitIdOrderByStartAtDesc(long unitId);

    List<RoomReservation> findByUnitIdAndStatusOrderByStartAtAsc(long unitId, ReservationStatus status);

    List<RoomReservation> findByUnitIdAndRequesterUserIdOrderByStartAtDesc(long unitId, long requesterUserId);

    List<RoomReservation> findByRoomIdInAndStatus(Collection<Long> roomIds, ReservationStatus status);

    List<RoomReservation> findByRoomIdInAndStatusIn(Collection<Long> roomIds, Collection<ReservationStatus> statuses);

    List<RoomReservation> findByTestDataTrue();
}
