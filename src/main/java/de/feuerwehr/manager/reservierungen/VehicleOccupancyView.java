package de.feuerwehr.manager.reservierungen;

import java.time.Instant;
import java.util.List;

public record VehicleOccupancyView(
        Instant rangeStart,
        Instant rangeEnd,
        List<VehicleOccupancyVehicleView> vehicles,
        List<VehicleOccupancyBookingView> bookings) {}
