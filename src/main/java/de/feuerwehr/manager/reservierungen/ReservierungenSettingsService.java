package de.feuerwehr.manager.reservierungen;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservierungenSettingsService {

    private final UnitReservierungenSettingsRepository settingsRepository;
    private final UnitRepository unitRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public UnitReservierungenSettings ensureSettings(long unitId) {
        return settingsRepository.findById(unitId).orElseGet(() -> createDefaults(unitId));
    }

    @Transactional
    public UnitReservierungenSettings saveVehicleSettings(
            long unitId,
            String vehicleSortMode,
            boolean vehicleDiveraEnabled,
            boolean vehicleGoogleCalendarEnabled,
            String vehicleDiveraDefaultGroupId,
            String vehicleDiveraGroupsJson,
            boolean vehicleLoeschWarnEnabled,
            int vehicleLoeschMinAvailable,
            List<Long> vehicleLoeschVehicleIds,
            List<Long> vehicleNotificationUserIds) {
        UnitReservierungenSettings settings = ensureSettings(unitId);
        settings.setVehicleSortMode(normalizeSortMode(vehicleSortMode));
        settings.setVehicleDiveraEnabled(vehicleDiveraEnabled);
        settings.setVehicleGoogleCalendarEnabled(vehicleGoogleCalendarEnabled);
        settings.setVehicleDiveraDefaultGroupId(trimToNull(vehicleDiveraDefaultGroupId));
        settings.setVehicleDiveraGroupsJson(trimToNull(vehicleDiveraGroupsJson));
        settings.setVehicleLoeschWarnEnabled(vehicleLoeschWarnEnabled);
        settings.setVehicleLoeschMinAvailable(Math.max(0, vehicleLoeschMinAvailable));
        settings.setVehicleLoeschVehicleIdsJson(writeJsonLongList(vehicleLoeschVehicleIds));
        settings.setVehicleNotificationUserIdsJson(writeJsonLongList(vehicleNotificationUserIds));
        return settingsRepository.save(settings);
    }

    @Transactional
    public UnitReservierungenSettings saveRoomSettings(
            long unitId,
            String roomSortMode,
            boolean roomDiveraEnabled,
            boolean roomGoogleCalendarEnabled,
            String roomDiveraDefaultGroupId,
            List<Long> roomNotificationUserIds) {
        UnitReservierungenSettings settings = ensureSettings(unitId);
        settings.setRoomSortMode(normalizeSortMode(roomSortMode));
        settings.setRoomDiveraEnabled(roomDiveraEnabled);
        settings.setRoomGoogleCalendarEnabled(roomGoogleCalendarEnabled);
        settings.setRoomDiveraDefaultGroupId(trimToNull(roomDiveraDefaultGroupId));
        settings.setRoomNotificationUserIdsJson(writeJsonLongList(roomNotificationUserIds));
        return settingsRepository.save(settings);
    }

    public List<Long> parseLongIdList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Long> ids = objectMapper.readValue(json, new TypeReference<>() {});
            if (ids == null) {
                return List.of();
            }
            return ids.stream().filter(id -> id != null && id > 0).distinct().toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Long> vehicleNotificationUserIds(UnitReservierungenSettings settings) {
        return parseLongIdList(settings.getVehicleNotificationUserIdsJson());
    }

    public List<Long> roomNotificationUserIds(UnitReservierungenSettings settings) {
        List<Long> roomIds = parseLongIdList(settings.getRoomNotificationUserIdsJson());
        if (!roomIds.isEmpty()) {
            return roomIds;
        }
        return vehicleNotificationUserIds(settings);
    }

    public List<Long> loeschVehicleIds(UnitReservierungenSettings settings) {
        return parseLongIdList(settings.getVehicleLoeschVehicleIdsJson());
    }

    public List<Integer> defaultDiveraGroupIds(UnitReservierungenSettings settings, boolean room) {
        String defaultId = room ? settings.getRoomDiveraDefaultGroupId() : settings.getVehicleDiveraDefaultGroupId();
        if (defaultId == null || defaultId.isBlank()) {
            return List.of();
        }
        try {
            int parsed = Integer.parseInt(defaultId.trim());
            return parsed > 0 ? List.of(parsed) : List.of();
        } catch (NumberFormatException e) {
            return List.of();
        }
    }

    private UnitReservierungenSettings createDefaults(long unitId) {
        Unit unit = unitRepository.findById(unitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        UnitReservierungenSettings settings = new UnitReservierungenSettings();
        settings.setUnit(unit);
        return settingsRepository.save(settings);
    }

    private String writeJsonLongList(List<Long> ids) {
        List<Long> normalized = ids == null
                ? List.of()
                : ids.stream().filter(id -> id != null && id > 0).distinct().sorted(Comparator.naturalOrder()).toList();
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String normalizeSortMode(String raw) {
        if (raw == null) {
            return "manual";
        }
        String mode = raw.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "name", "created" -> mode;
            default -> "manual";
        };
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
