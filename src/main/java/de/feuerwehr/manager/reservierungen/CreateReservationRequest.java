package de.feuerwehr.manager.reservierungen;

import java.time.Instant;
import java.util.List;

public record CreateReservationRequest(
        /** Abwärtskompatibel: einzelnes Fahrzeug/Raum. */
        Long resourceId,
        /** Mehrere Fahrzeuge bzw. Räume. */
        List<Long> resourceIds,
        String requesterName,
        String requesterEmail,
        String reason,
        String location,
        /** Abwärtskompatibel: einzelner Zeitraum. */
        Instant startAt,
        Instant endAt,
        /** Mehrere Termine in einem Antrag. */
        List<ReservationTimeSlot> slots,
        boolean forceAvailabilityOverride,
        boolean forceConflictOverride) {

    public record ReservationTimeSlot(Instant startAt, Instant endAt) {}
}
