package de.feuerwehr.manager.berichte;

import java.util.List;

public record KraefteFahrzeugeState(
        List<KraeftePersonView> manualPersons,
        List<KraeftePersonView> diveraPersons,
        List<KraefteVehicleView> vehicles) {

    public record KraeftePersonView(long id, String displayName, String qualTier, int sortOrder) {}

    public record KraefteVehicleView(
            long vehicleId,
            String name,
            List<Long> crewPersonIds,
            List<KraeftePersonView> crewPersons,
            String besatzungsstaerke) {}
}
