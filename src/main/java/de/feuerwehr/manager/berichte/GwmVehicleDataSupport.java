package de.feuerwehr.manager.berichte;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class GwmVehicleDataSupport {

    private static final TypeReference<List<GwmVehiclePayload>> LIST_TYPE = new TypeReference<>() {};

    private GwmVehicleDataSupport() {}

    public static List<GwmVehicleData> parse(String vehiclesJson, String legacyDeployedJson, ObjectMapper mapper) {
        List<GwmVehicleData> fromVehicles = parseVehiclesJson(vehiclesJson, mapper);
        if (!fromVehicles.isEmpty()) {
            return fromVehicles;
        }
        return fromDeployedEquipment(legacyDeployedJson, mapper);
    }

    public static List<GwmVehicleData> parseVehiclesJson(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<GwmVehiclePayload> payloads = mapper.readValue(json, LIST_TYPE);
            List<GwmVehicleData> result = new ArrayList<>();
            for (GwmVehiclePayload payload : payloads) {
                if (payload == null || payload.vehicleId() == null) {
                    continue;
                }
                result.add(new GwmVehicleData(
                        payload.vehicleId(),
                        payload.maschinistPersonId(),
                        payload.einheitsfuehrerPersonId(),
                        normalizeIds(payload.equipmentIds()),
                        normalizeIds(payload.defectiveEquipmentIds()),
                        trimOrNull(payload.defectiveFreitext()),
                        trimOrNull(payload.defectiveMangel())));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<GwmVehicleData> fromDeployedEquipment(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<DeployedEquipmentPayload> payloads = mapper.readValue(json, new TypeReference<>() {});
            List<GwmVehicleData> result = new ArrayList<>();
            for (DeployedEquipmentPayload payload : payloads) {
                if (payload == null || payload.vehicleId() == null) {
                    continue;
                }
                result.add(new GwmVehicleData(payload.vehicleId(), normalizeIds(payload.equipmentIds())));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String toDeployedEquipmentJson(List<GwmVehicleData> vehicles, ObjectMapper mapper) {
        List<DeployedEquipmentAssignment> assignments = vehicles.stream()
                .map(v -> new DeployedEquipmentAssignment(
                        v.vehicleId(), v.equipmentIds() != null ? v.equipmentIds() : List.of()))
                .toList();
        try {
            return mapper.writeValueAsString(assignments);
        } catch (Exception e) {
            return "[]";
        }
    }

    public static String serialize(List<GwmVehicleData> vehicles, ObjectMapper mapper) {
        if (vehicles == null || vehicles.isEmpty()) {
            return "[]";
        }
        try {
            return mapper.writeValueAsString(vehicles);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).toList();
    }

    private static String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record GwmVehiclePayload(
            Long vehicleId,
            Long maschinistPersonId,
            Long einheitsfuehrerPersonId,
            List<Long> equipmentIds,
            List<Long> defectiveEquipmentIds,
            String defectiveFreitext,
            String defectiveMangel) {}

    private record DeployedEquipmentPayload(Long vehicleId, List<Long> equipmentIds) {}
}
