package de.feuerwehr.manager.reservierungen;

import java.time.Instant;

public record VehicleOccupancyBookingView(
        long reservationId,
        long vehicleId,
        Instant startAt,
        Instant endAt,
        ReservationStatus status,
        String reason,
        String location,
        String requesterName) {}
