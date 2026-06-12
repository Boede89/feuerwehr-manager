package de.feuerwehr.manager.berichte;

import java.util.List;

public final class KraefteCrewJsonSupport {

    private KraefteCrewJsonSupport() {}

    public static String buildCrewJson(KraefteFahrzeugeState state) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        first = appendCrewAssignment(sb, state.beteiligt(), first);
        first = appendCrewAssignment(sb, state.einsatzstelle(), first);
        first = appendCrewAssignment(sb, state.wache(), first);
        for (KraefteFahrzeugeState.KraefteVehicleView vehicle : state.vehicles()) {
            first = appendCrewAssignment(sb, vehicle, first);
        }
        sb.append(']');
        return sb.toString();
    }

    private static boolean appendCrewAssignment(
            StringBuilder sb, KraefteFahrzeugeState.KraefteVehicleView vehicle, boolean first) {
        if (!first) {
            sb.append(',');
        }
        sb.append("{\"vehicleId\":").append(vehicle.vehicleId()).append(",\"personIds\":[");
        List<Long> ids = vehicle.crewPersonIds();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids.get(i));
        }
        sb.append(']');
        if (vehicle.einheitsfuehrerPersonId() != null) {
            sb.append(",\"einheitsfuehrerPersonId\":").append(vehicle.einheitsfuehrerPersonId());
        }
        if (vehicle.maschinistPersonId() != null) {
            sb.append(",\"maschinistPersonId\":").append(vehicle.maschinistPersonId());
        }
        List<Long> paIds = vehicle.crewPersons().stream()
                .filter(KraefteFahrzeugeState.KraeftePersonView::usesPa)
                .map(KraefteFahrzeugeState.KraeftePersonView::id)
                .toList();
        if (!paIds.isEmpty()) {
            sb.append(",\"paPersonIds\":[");
            for (int i = 0; i < paIds.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(paIds.get(i));
            }
            sb.append(']');
        }
        if (vehicle.vehicleId() > 0) {
            sb.append(",\"involvedInIncident\":").append(vehicle.involvedInIncident());
            sb.append(",\"manuallyInvolvedInIncident\":").append(vehicle.manuallyInvolvedInIncident());
        }
        sb.append('}');
        return false;
    }
}
