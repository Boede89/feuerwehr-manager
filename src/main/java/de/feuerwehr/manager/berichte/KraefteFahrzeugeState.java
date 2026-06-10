package de.feuerwehr.manager.berichte;

import java.util.List;

public record KraefteFahrzeugeState(
        List<KraeftePersonView> manualPersons,
        List<KraeftePersonView> diveraPersons,
        KraefteVehicleView beteiligt,
        KraefteVehicleView einsatzstelle,
        KraefteVehicleView wache,
        List<KraefteVehicleView> vehicles) {

    public List<KraefteVehicleView> involvedVehicles() {
        if (vehicles == null || vehicles.isEmpty()) {
            return List.of();
        }
        return vehicles.stream().filter(KraefteVehicleView::involvedInIncident).toList();
    }

    public record KraeftePersonView(
            long id,
            String displayName,
            String qualTier,
            int sortOrder,
            String vehicleRole,
            boolean usesPa,
            String poolSource) {}

    public record KraefteVehicleView(
            long vehicleId,
            String name,
            String vehicleType,
            String vehicleTypeLabel,
            List<Long> crewPersonIds,
            List<KraeftePersonView> crewPersons,
            String besatzungsstaerke,
            Long einheitsfuehrerPersonId,
            Long maschinistPersonId,
            boolean involvedInIncident,
            boolean manuallyInvolvedInIncident) {}
}
