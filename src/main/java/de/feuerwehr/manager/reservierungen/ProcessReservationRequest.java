package de.feuerwehr.manager.reservierungen;

import java.util.List;

public record ProcessReservationRequest(
        String action,
        String reason,
        boolean forceAvailabilityOverride,
        List<Long> conflictIds,
        List<Integer> diveraGroupIds,
        /** Bei Genehmigung: nur diese Fahrzeuge freigeben (null/leer = alle). */
        List<Long> approvedVehicleIds,
        /** Bei Genehmigung: diese Fahrzeuge ablehnen (optional, Rest = genehmigt). */
        List<Long> rejectedVehicleIds) {}
