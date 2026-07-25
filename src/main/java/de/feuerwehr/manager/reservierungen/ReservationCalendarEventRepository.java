package de.feuerwehr.manager.reservierungen;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationCalendarEventRepository extends JpaRepository<ReservationCalendarEvent, Long> {

    List<ReservationCalendarEvent> findAllByReservationKindAndReservationId(
            ReservationKind kind, long reservationId);

    Optional<ReservationCalendarEvent> findByReservationKindAndReservationIdAndCalendarAccountId(
            ReservationKind kind, long reservationId, Long calendarAccountId);
}
