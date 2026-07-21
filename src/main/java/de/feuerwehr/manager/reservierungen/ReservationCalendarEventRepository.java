package de.feuerwehr.manager.reservierungen;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationCalendarEventRepository extends JpaRepository<ReservationCalendarEvent, Long> {

    Optional<ReservationCalendarEvent> findByReservationKindAndReservationId(
            ReservationKind kind, long reservationId);
}
