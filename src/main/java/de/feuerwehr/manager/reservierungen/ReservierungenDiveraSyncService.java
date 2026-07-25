package de.feuerwehr.manager.reservierungen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.feuerwehr.manager.divera.DiveraApiClient;
import de.feuerwehr.manager.unit.UnitDiveraSettings;
import de.feuerwehr.manager.unit.UnitDiveraSettingsRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservierungenDiveraSyncService {

    private final DiveraApiClient diveraApiClient;
    private final UnitDiveraSettingsRepository diveraSettingsRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public Optional<Long> syncVehicleReservation(VehicleReservation reservation, List<Integer> groupIds, Long actorUserId) {
        return syncReservation(
                reservation.getUnit().getId(),
                reservation.getId(),
                reservation.getVehicle().getName(),
                reservation.getReason(),
                reservation.getLocation(),
                reservation.getStartAt(),
                reservation.getEndAt(),
                groupIds,
                actorUserId);
    }

    public Optional<Long> syncRoomReservation(RoomReservation reservation, List<Integer> groupIds, Long actorUserId) {
        return syncReservation(
                reservation.getUnit().getId(),
                reservation.getId(),
                reservation.getRoom().getName(),
                reservation.getReason(),
                reservation.getLocation(),
                reservation.getStartAt(),
                reservation.getEndAt(),
                groupIds,
                actorUserId);
    }

    public void deleteEvent(long unitId, Long diveraEventId, Long actorUserId) {
        if (diveraEventId == null || diveraEventId <= 0) {
            return;
        }
        Optional<DiveraCredentials> credentials = resolveCredentials(unitId, actorUserId);
        if (credentials.isEmpty()) {
            return;
        }
        DiveraCredentials cred = credentials.get();
        DiveraApiClient.DiveraMutationResult result =
                diveraApiClient.deleteEvent(cred.apiBaseUrl(), cred.accessKey(), diveraEventId);
        if (!result.success()) {
            log.warn("Divera-Event {} konnte nicht gelöscht werden: {}", diveraEventId, result.message());
        }
    }

    private Optional<Long> syncReservation(
            long unitId,
            long reservationId,
            String resourceName,
            String reason,
            String location,
            Instant startAt,
            Instant endAt,
            List<Integer> groupIds,
            Long actorUserId) {
        Optional<DiveraCredentials> credentials = resolveCredentials(unitId, actorUserId);
        if (credentials.isEmpty()) {
            log.warn(
                    "DIVERA-Sync übersprungen (Reservierung {}): kein Access Key (Einheit oder Genehmiger).",
                    reservationId);
            return Optional.empty();
        }
        DiveraCredentials cred = credentials.get();
        ObjectNode event = objectMapper.createObjectNode();
        boolean useGroups = groupIds != null && !groupIds.isEmpty();
        event.put("notification_type", useGroups ? 3 : 2);
        event.put("title", resourceName + " - " + (reason != null ? reason : "Reservierung"));
        event.put("text", reason != null ? reason : "Reservierung");
        event.put("ts_start", startAt.getEpochSecond());
        event.put("ts_end", endAt.getEpochSecond());
        if (location != null && !location.isBlank()) {
            event.put("address", location.trim());
        }
        event.put("foreign_id", String.valueOf(reservationId));
        if (useGroups) {
            var groupArray = event.putArray("group");
            groupIds.forEach(groupArray::add);
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.set("Event", event);
        if (useGroups) {
            var usingGroups = body.putArray("usingGroups");
            groupIds.forEach(usingGroups::add);
        }
        DiveraApiClient.DiveraMutationResult result =
                diveraApiClient.createEvent(cred.apiBaseUrl(), cred.accessKey(), body);
        if (!result.success()) {
            log.warn(
                    "Divera-Reservierung {} konnte nicht übertragen werden: {} – {}",
                    reservationId,
                    result.message(),
                    result.body());
            return Optional.empty();
        }
        Optional<Long> eventId = parseEventId(result.body());
        if (eventId.isEmpty()) {
            log.warn(
                    "DIVERA-Event angelegt, aber ID nicht lesbar (Reservierung {}). Body={}",
                    reservationId,
                    result.body());
        }
        return eventId;
    }

    private Optional<Long> parseEventId(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            long id = root.path("data").path("id").asLong(0);
            if (id <= 0) {
                id = root.path("data").path("Event").path("id").asLong(0);
            }
            if (id <= 0) {
                id = root.path("id").asLong(0);
            }
            return id > 0 ? Optional.of(id) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<DiveraCredentials> resolveCredentials(long unitId, Long actorUserId) {
        String accessKey = null;
        if (actorUserId != null) {
            accessKey = userRepository.findById(actorUserId).map(User::getDiveraApiKey).orElse(null);
        }
        UnitDiveraSettings unitSettings =
                diveraSettingsRepository.findByUnitId(unitId).orElse(null);
        String apiBase = unitSettings != null && unitSettings.getApiBaseUrl() != null
                ? unitSettings.getApiBaseUrl()
                : "https://app.divera247.com";
        if (accessKey == null || accessKey.isBlank()) {
            accessKey = unitSettings != null ? unitSettings.getAccessKey() : null;
        }
        if (accessKey == null || accessKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new DiveraCredentials(apiBase, accessKey.trim()));
    }

    private record DiveraCredentials(String apiBaseUrl, String accessKey) {}
}
