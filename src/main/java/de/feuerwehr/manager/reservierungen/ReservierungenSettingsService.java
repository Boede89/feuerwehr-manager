package de.feuerwehr.manager.reservierungen;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.technik.Vehicle;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservierungenSettingsService {

    private final UnitReservierungenSettingsRepository settingsRepository;
    private final UnitRepository unitRepository;
    private final ModuleSettingsService moduleSettingsService;
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
            List<Long> vehicleGoogleCalendarAccountIds,
            String vehicleDiveraDefaultGroupId,
            String vehicleDiveraGroupsJson,
            boolean vehicleLoeschWarnEnabled,
            int vehicleLoeschMinAvailable,
            List<Long> vehicleLoeschVehicleIds) {
        UnitReservierungenSettings settings = ensureSettings(unitId);
        settings.setVehicleSortMode(normalizeSortMode(vehicleSortMode));
        settings.setVehicleDiveraEnabled(vehicleDiveraEnabled);
        settings.setVehicleGoogleCalendarEnabled(vehicleGoogleCalendarEnabled);
        settings.setVehicleGoogleCalendarAccountIdsJson(
                vehicleGoogleCalendarEnabled
                        ? writeJsonLongListPreserveOrder(vehicleGoogleCalendarAccountIds)
                        : "[]");
        settings.setVehicleDiveraDefaultGroupId(trimToNull(vehicleDiveraDefaultGroupId));
        settings.setVehicleDiveraGroupsJson(trimToNull(vehicleDiveraGroupsJson));
        settings.setVehicleLoeschWarnEnabled(vehicleLoeschWarnEnabled);
        settings.setVehicleLoeschMinAvailable(Math.max(0, vehicleLoeschMinAvailable));
        settings.setVehicleLoeschVehicleIdsJson(writeJsonLongList(vehicleLoeschVehicleIds));
        return settingsRepository.save(settings);
    }

    @Transactional
    public UnitReservierungenSettings saveVehicleNotifications(
            long unitId, List<Long> userIds, List<String> emails) {
        UnitReservierungenSettings settings = ensureSettings(unitId);
        settings.setVehicleNotificationUserIdsJson(writeJsonLongList(userIds));
        settings.setVehicleNotificationEmailsJson(writeJsonEmailList(emails));
        return settingsRepository.save(settings);
    }

    @Transactional
    public UnitReservierungenSettings saveVehicleSortOrder(long unitId, List<Long> orderedVehicleIds) {
        UnitReservierungenSettings settings = ensureSettings(unitId);
        settings.setVehicleSortMode("manual");
        settings.setVehicleSortOrderJson(writeJsonLongListPreserveOrder(orderedVehicleIds));
        return settingsRepository.save(settings);
    }

    @Transactional
    public UnitReservierungenSettings saveRoomSettings(
            long unitId,
            String roomSortMode,
            boolean roomDiveraEnabled,
            boolean roomGoogleCalendarEnabled,
            List<Long> roomGoogleCalendarAccountIds,
            String roomDiveraDefaultGroupId) {
        UnitReservierungenSettings settings = ensureSettings(unitId);
        settings.setRoomSortMode(normalizeSortMode(roomSortMode));
        settings.setRoomDiveraEnabled(roomDiveraEnabled);
        settings.setRoomGoogleCalendarEnabled(roomGoogleCalendarEnabled);
        settings.setRoomGoogleCalendarAccountIdsJson(
                roomGoogleCalendarEnabled
                        ? writeJsonLongListPreserveOrder(roomGoogleCalendarAccountIds)
                        : "[]");
        settings.setRoomDiveraDefaultGroupId(trimToNull(roomDiveraDefaultGroupId));
        return settingsRepository.save(settings);
    }

    @Transactional
    public UnitReservierungenSettings saveRoomNotifications(
            long unitId, List<Long> userIds, List<String> emails) {
        UnitReservierungenSettings settings = ensureSettings(unitId);
        settings.setRoomNotificationUserIdsJson(writeJsonLongList(userIds));
        settings.setRoomNotificationEmailsJson(writeJsonEmailList(emails));
        return settingsRepository.save(settings);
    }

    @Transactional
    public UnitReservierungenSettings saveAccessSettings(long unitId, boolean allowPublicReservation) {
        UnitReservierungenSettings settings = ensureSettings(unitId);
        settings.setAllowPublicReservation(allowPublicReservation);
        return settingsRepository.save(settings);
    }

    @Transactional(readOnly = true)
    public boolean isPublicReservationAllowed(long unitId) {
        return settingsRepository.findById(unitId).map(UnitReservierungenSettings::isAllowPublicReservation).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isPublicReservationOpen(long unitId) {
        return moduleSettingsService.isEnabled(AppModule.RESERVIERUNGEN, unitId) && isPublicReservationAllowed(unitId);
    }

    @Transactional(readOnly = true)
    public List<Unit> listUnitsAllowingPublicReservation() {
        return unitRepository.findActiveVisible(false).stream()
                .filter(unit -> isPublicReservationOpen(unit.getId()))
                .toList();
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

    public List<Long> vehicleSortOrderIds(UnitReservierungenSettings settings) {
        return parseLongIdList(settings.getVehicleSortOrderJson());
    }

    /**
     * Sortiert Fahrzeuge nach den Reservierungen-Einstellungen.
     * {@code manual}: eigene Reihenfolge (unabhängig von Technik); fehlende IDs ans Ende.
     */
    public List<Vehicle> sortVehicles(UnitReservierungenSettings settings, List<Vehicle> vehicles) {
        if (vehicles == null || vehicles.isEmpty()) {
            return List.of();
        }
        String mode = normalizeSortMode(settings.getVehicleSortMode());
        return switch (mode) {
            case "name" -> vehicles.stream()
                    .sorted(Comparator.comparing(
                            v -> v.getName() != null ? v.getName().toLowerCase(Locale.GERMAN) : "",
                            Comparator.naturalOrder()))
                    .toList();
            case "created" -> vehicles.stream()
                    .sorted(Comparator.comparing(
                                    Vehicle::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(
                                    v -> v.getName() != null ? v.getName().toLowerCase(Locale.GERMAN) : "",
                                    Comparator.naturalOrder()))
                    .toList();
            default -> applyManualVehicleOrder(vehicles, vehicleSortOrderIds(settings));
        };
    }

    public List<Long> vehicleNotificationUserIds(UnitReservierungenSettings settings) {
        return parseLongIdList(settings.getVehicleNotificationUserIdsJson());
    }

    public List<String> vehicleNotificationEmails(UnitReservierungenSettings settings) {
        return parseEmailList(settings.getVehicleNotificationEmailsJson());
    }

    /** Explizit für Raum hinterlegte Benutzer (ohne Fallback auf Fahrzeug). */
    public List<Long> roomNotificationUserIdsStored(UnitReservierungenSettings settings) {
        return parseLongIdList(settings.getRoomNotificationUserIdsJson());
    }

    public List<String> roomNotificationEmailsStored(UnitReservierungenSettings settings) {
        return parseEmailList(settings.getRoomNotificationEmailsJson());
    }

    /**
     * Empfänger für neue Raum-Anträge. Leer (keine Benutzer, keine E-Mails) → wie Fahrzeug.
     */
    public NotificationRecipients roomNotificationRecipients(UnitReservierungenSettings settings) {
        List<Long> userIds = roomNotificationUserIdsStored(settings);
        List<String> emails = roomNotificationEmailsStored(settings);
        if (userIds.isEmpty() && emails.isEmpty()) {
            return vehicleNotificationRecipients(settings);
        }
        return new NotificationRecipients(userIds, emails);
    }

    public NotificationRecipients vehicleNotificationRecipients(UnitReservierungenSettings settings) {
        return new NotificationRecipients(
                vehicleNotificationUserIds(settings), vehicleNotificationEmails(settings));
    }

    /** @deprecated Nutze {@link #roomNotificationUserIdsStored} bzw. Recipients. */
    public List<Long> roomNotificationUserIds(UnitReservierungenSettings settings) {
        return roomNotificationRecipients(settings).userIds();
    }

    public record NotificationRecipients(List<Long> userIds, List<String> emails) {
        public boolean isEmpty() {
            return (userIds == null || userIds.isEmpty()) && (emails == null || emails.isEmpty());
        }
    }

    public List<String> parseEmailList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> raw = objectMapper.readValue(json, new TypeReference<>() {});
            if (raw == null) {
                return List.of();
            }
            return normalizeEmails(raw);
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<String> parseEmailsFromText(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split("[,;\\s]+");
        return normalizeEmails(Arrays.asList(parts));
    }

    private static List<String> normalizeEmails(List<String> raw) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String email = entry.trim().toLowerCase(Locale.ROOT);
            if (email.isEmpty() || !email.contains("@") || email.length() > 254) {
                continue;
            }
            result.add(email);
        }
        return List.copyOf(result);
    }

    public List<Long> loeschVehicleIds(UnitReservierungenSettings settings) {
        return parseLongIdList(settings.getVehicleLoeschVehicleIdsJson());
    }

    public List<Long> vehicleGoogleCalendarAccountIds(UnitReservierungenSettings settings) {
        return parseLongIdListPreserveOrder(settings.getVehicleGoogleCalendarAccountIdsJson());
    }

    public List<Long> roomGoogleCalendarAccountIds(UnitReservierungenSettings settings) {
        return parseLongIdListPreserveOrder(settings.getRoomGoogleCalendarAccountIdsJson());
    }

    public List<Long> parseLongIdListPreserveOrder(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Long> ids = objectMapper.readValue(json, new TypeReference<>() {});
            if (ids == null) {
                return List.of();
            }
            List<Long> result = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (Long id : ids) {
                if (id != null && id > 0 && seen.add(id)) {
                    result.add(id);
                }
            }
            return List.copyOf(result);
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Integer> defaultDiveraGroupIds(UnitReservierungenSettings settings, boolean room) {
        String defaultId = room ? settings.getRoomDiveraDefaultGroupId() : settings.getVehicleDiveraDefaultGroupId();
        if (defaultId == null || defaultId.isBlank()) {
            return List.of();
        }
        String trimmed = defaultId.trim();
        // „Alle“ / Gruppe ohne DIVERA-ID → keine group an DIVERA
        if ("ALL".equalsIgnoreCase(trimmed) || "alle".equalsIgnoreCase(trimmed)) {
            return List.of();
        }
        try {
            int parsed = Integer.parseInt(trimmed);
            return parsed > 0 ? List.of(parsed) : List.of();
        } catch (NumberFormatException e) {
            return List.of();
        }
    }

    private static List<Vehicle> applyManualVehicleOrder(List<Vehicle> vehicles, List<Long> orderIds) {
        Map<Long, Vehicle> byId = new HashMap<>();
        for (Vehicle v : vehicles) {
            if (v.getId() != null) {
                byId.put(v.getId(), v);
            }
        }
        List<Vehicle> result = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (Long id : orderIds) {
            Vehicle v = byId.get(id);
            if (v != null && used.add(id)) {
                result.add(v);
            }
        }
        for (Vehicle v : vehicles) {
            if (v.getId() != null && used.add(v.getId())) {
                result.add(v);
            }
        }
        return result;
    }

    private UnitReservierungenSettings createDefaults(long unitId) {
        Unit unit = unitRepository
                .findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        UnitReservierungenSettings settings = new UnitReservierungenSettings();
        settings.setUnit(unit);
        return settingsRepository.save(settings);
    }

    private String writeJsonLongList(List<Long> ids) {
        List<Long> normalized = ids == null
                ? List.of()
                : ids.stream()
                        .filter(id -> id != null && id > 0)
                        .distinct()
                        .sorted(Comparator.naturalOrder())
                        .toList();
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String writeJsonEmailList(List<String> emails) {
        List<String> normalized = normalizeEmails(emails == null ? List.of() : emails);
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** Reihenfolge bleibt erhalten (für manuelle Sortierung). */
    private String writeJsonLongListPreserveOrder(List<Long> ids) {
        List<Long> normalized = new ArrayList<>();
        if (ids != null) {
            Set<Long> seen = new HashSet<>();
            for (Long id : ids) {
                if (id != null && id > 0 && seen.add(id)) {
                    normalized.add(id);
                }
            }
        }
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
