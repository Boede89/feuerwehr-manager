package de.feuerwehr.manager.berichte;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class GwmVehicleDataSupport {

    private static final TypeReference<List<GwmVehiclePayload>> LIST_TYPE = new TypeReference<>() {};

    private GwmVehicleDataSupport() {}

    public static List<GwmVehicleData> parse(String vehiclesJson, String legacyDeployedJson, ObjectMapper mapper) {
        return mergeVehicles(
                parseVehiclesJson(vehiclesJson, mapper), fromDeployedEquipment(legacyDeployedJson, mapper));
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
                List<Long> defectiveIds = normalizeIds(payload.defectiveEquipmentIds());
                Map<Long, String> mangelByEquipment = normalizeMangelMap(payload.defectiveMangelByEquipmentId());
                if (mangelByEquipment.isEmpty()
                        && payload.defectiveMangel() != null
                        && !payload.defectiveMangel().isBlank()
                        && !defectiveIds.isEmpty()) {
                    mangelByEquipment = legacyMangelForAll(defectiveIds, payload.defectiveMangel());
                }
                result.add(new GwmVehicleData(
                        payload.vehicleId(),
                        payload.maschinistPersonId(),
                        payload.einheitsfuehrerPersonId(),
                        normalizeIds(payload.equipmentIds()),
                        defectiveIds,
                        mangelByEquipment,
                        trimOrNull(payload.defectiveFreitext()),
                        trimOrNull(payload.defectiveFreitextMangel() != null
                                ? payload.defectiveFreitextMangel()
                                : payload.defectiveMangel()),
                        normalizeCustom(payload.customEquipment())));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Kombiniert Fahrzeuge aus vehiclesDataJson mit zusätzlichen Geräte-Zuordnungen
     * (z. B. Geräte von Fahrzeugen, die nicht als „eingesetzt“ markiert sind).
     */
    public static List<GwmVehicleData> mergeVehicles(
            List<GwmVehicleData> primary, List<GwmVehicleData> equipmentOnly) {
        Map<Long, GwmVehicleData> byId = new LinkedHashMap<>();
        if (primary != null) {
            for (GwmVehicleData vehicle : primary) {
                if (vehicle != null) {
                    byId.put(vehicle.vehicleId(), vehicle);
                }
            }
        }
        if (equipmentOnly != null) {
            for (GwmVehicleData extra : equipmentOnly) {
                if (extra == null) {
                    continue;
                }
                GwmVehicleData existing = byId.get(extra.vehicleId());
                if (existing == null) {
                    byId.put(extra.vehicleId(), extra);
                    continue;
                }
                byId.put(extra.vehicleId(), mergeEquipment(existing, extra));
            }
        }
        return List.copyOf(byId.values());
    }

    private static GwmVehicleData mergeEquipment(GwmVehicleData base, GwmVehicleData extra) {
        Set<Long> equipmentIds = new LinkedHashSet<>();
        if (base.equipmentIds() != null) {
            equipmentIds.addAll(base.equipmentIds());
        }
        if (extra.equipmentIds() != null) {
            equipmentIds.addAll(extra.equipmentIds());
        }
        List<CustomDeployedEquipment> custom = new ArrayList<>();
        Set<String> customKeys = new LinkedHashSet<>();
        appendCustom(custom, customKeys, base.customEquipment());
        appendCustom(custom, customKeys, extra.customEquipment());
        return new GwmVehicleData(
                base.vehicleId(),
                base.maschinistPersonId() != null ? base.maschinistPersonId() : extra.maschinistPersonId(),
                base.einheitsfuehrerPersonId() != null
                        ? base.einheitsfuehrerPersonId()
                        : extra.einheitsfuehrerPersonId(),
                List.copyOf(equipmentIds),
                base.defectiveEquipmentIds(),
                base.defectiveMangelByEquipmentId(),
                base.defectiveFreitext() != null ? base.defectiveFreitext() : extra.defectiveFreitext(),
                base.defectiveFreitextMangel() != null
                        ? base.defectiveFreitextMangel()
                        : extra.defectiveFreitextMangel(),
                List.copyOf(custom));
    }

    private static void appendCustom(
            List<CustomDeployedEquipment> target,
            Set<String> keys,
            List<CustomDeployedEquipment> source) {
        if (source == null) {
            return;
        }
        for (CustomDeployedEquipment item : source) {
            if (item == null || item.name() == null || item.name().isBlank()) {
                continue;
            }
            String key = item.name().trim().toLowerCase();
            if (!keys.add(key)) {
                continue;
            }
            target.add(new CustomDeployedEquipment(
                    item.name().trim(),
                    item.categoryName() != null && !item.categoryName().isBlank()
                            ? item.categoryName().trim()
                            : null));
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
                List<Long> equipmentIds = normalizeIds(payload.equipmentIds());
                List<CustomDeployedEquipment> custom = normalizeCustom(payload.customEquipment());
                if (equipmentIds.isEmpty() && custom.isEmpty()) {
                    continue;
                }
                result.add(new GwmVehicleData(
                        payload.vehicleId(),
                        null,
                        null,
                        equipmentIds,
                        List.of(),
                        Map.of(),
                        null,
                        null,
                        custom));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String toDeployedEquipmentJson(List<GwmVehicleData> vehicles, ObjectMapper mapper) {
        List<DeployedEquipmentAssignment> assignments = vehicles.stream()
                .filter(Objects::nonNull)
                .filter(GwmVehicleData::hasEquipment)
                .map(v -> new DeployedEquipmentAssignment(
                        v.vehicleId(),
                        v.equipmentIds() != null ? v.equipmentIds() : List.of(),
                        v.customEquipment() != null ? v.customEquipment() : List.of()))
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

    private static Map<Long, String> normalizeMangelMap(Map<Long, String> map) {
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key != null && value != null && !value.isBlank()) {
                result.put(key, value.trim());
            }
        });
        return result;
    }

    private static Map<Long, String> legacyMangelForAll(List<Long> defectiveIds, String mangel) {
        Map<Long, String> result = new LinkedHashMap<>();
        String text = mangel.trim();
        for (Long id : defectiveIds) {
            result.put(id, text);
        }
        return result;
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).toList();
    }

    private static List<CustomDeployedEquipment> normalizeCustom(List<CustomDeployedEquipment> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<CustomDeployedEquipment> result = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        appendCustom(result, keys, items);
        return List.copyOf(result);
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
            Map<Long, String> defectiveMangelByEquipmentId,
            String defectiveFreitext,
            String defectiveFreitextMangel,
            String defectiveMangel,
            List<CustomDeployedEquipment> customEquipment) {}

    private record DeployedEquipmentPayload(
            Long vehicleId, List<Long> equipmentIds, List<CustomDeployedEquipment> customEquipment) {}
}
