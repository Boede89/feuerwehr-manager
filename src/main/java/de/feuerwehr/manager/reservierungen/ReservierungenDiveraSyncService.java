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
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
        for (DiveraCredentials cred : resolveCredentialCandidates(unitId, actorUserId)) {
            DiveraApiClient.DiveraMutationResult result =
                    diveraApiClient.deleteEvent(cred.apiBaseUrl(), cred.accessKey(), diveraEventId);
            if (result.success()) {
                return;
            }
            log.warn(
                    "Divera-Event {} konnte nicht gelöscht werden ({}): {}",
                    diveraEventId,
                    cred.source(),
                    result.message());
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
        List<DiveraCredentials> candidates = resolveCredentialCandidates(unitId, actorUserId);
        if (candidates.isEmpty()) {
            log.warn(
                    "DIVERA-Sync übersprungen (Reservierung {}): kein Access Key (Einheit oder Genehmiger).",
                    reservationId);
            return Optional.empty();
        }

        ObjectNode body = buildEventBody(reservationId, resourceName, reason, location, startAt, endAt, groupIds);
        DiveraApiClient.DiveraMutationResult lastFailure = null;
        for (DiveraCredentials cred : candidates) {
            DiveraApiClient.DiveraMutationResult result =
                    diveraApiClient.createEvent(cred.apiBaseUrl(), cred.accessKey(), body);
            if (!result.success()) {
                lastFailure = result;
                log.warn(
                        "Divera-Reservierung {} fehlgeschlagen ({}): {} – {}",
                        reservationId,
                        cred.source(),
                        result.message(),
                        abbreviate(result.body()));
                continue;
            }
            Optional<Long> eventId = parseEventId(result.body());
            if (eventId.isEmpty()) {
                log.warn(
                        "DIVERA-Event angelegt, aber ID nicht lesbar (Reservierung {}, {}). Body={}",
                        reservationId,
                        cred.source(),
                        abbreviate(result.body()));
                return Optional.empty();
            }
            log.info(
                    "DIVERA-Termin {} für Reservierung {} angelegt ({}).",
                    eventId.get(),
                    reservationId,
                    cred.source());
            return eventId;
        }
        if (lastFailure != null) {
            log.warn(
                    "Divera-Reservierung {} konnte mit keinem Access Key übertragen werden: {}",
                    reservationId,
                    lastFailure.message());
        }
        return Optional.empty();
    }

    private ObjectNode buildEventBody(
            long reservationId,
            String resourceName,
            String reason,
            String location,
            Instant startAt,
            Instant endAt,
            List<Integer> groupIds) {
        ObjectNode event = objectMapper.createObjectNode();
        boolean useGroups = groupIds != null && !groupIds.isEmpty();
        // 2 = Alle des Standortes, 3 = Ausgewählte Gruppen (DIVERA API v2/events)
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
        return body;
    }

    private Optional<Long> parseEventId(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            long id = data.path("id").asLong(0);
            if (id <= 0) {
                id = data.path("Event").path("id").asLong(0);
            }
            if (id <= 0 && data.isObject()) {
                // data kann als Map id → Event geliefert werden
                var fields = data.fields();
                if (fields.hasNext()) {
                    var first = fields.next();
                    id = first.getValue().path("id").asLong(0);
                    if (id <= 0) {
                        try {
                            id = Long.parseLong(first.getKey());
                        } catch (NumberFormatException ignored) {
                            id = 0;
                        }
                    }
                }
            }
            if (id <= 0) {
                id = root.path("id").asLong(0);
            }
            return id > 0 ? Optional.of(id) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Persönlicher Access Key des Genehmigers zuerst (Termin erscheint unter dessen DIVERA-Konto),
     * danach Einheits-Access-Key als Fallback.
     */
    private List<DiveraCredentials> resolveCredentialCandidates(long unitId, Long actorUserId) {
        UnitDiveraSettings unitSettings =
                diveraSettingsRepository.findByUnitId(unitId).orElse(null);
        String apiBase = unitSettings != null && unitSettings.getApiBaseUrl() != null
                ? unitSettings.getApiBaseUrl()
                : "https://app.divera247.com";

        List<DiveraCredentials> result = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        if (actorUserId != null) {
            userRepository.findById(actorUserId).map(User::getDiveraApiKey).ifPresent(raw -> {
                if (raw != null && !raw.isBlank()) {
                    String key = raw.trim();
                    if (seen.add(key)) {
                        result.add(new DiveraCredentials(apiBase, key, "user-api-key"));
                    }
                }
            });
        }
        if (unitSettings != null && unitSettings.getAccessKey() != null && !unitSettings.getAccessKey().isBlank()) {
            String key = unitSettings.getAccessKey().trim();
            if (seen.add(key)) {
                result.add(new DiveraCredentials(apiBase, key, "unit-access-key"));
            }
        }
        return result;
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 300 ? value.substring(0, 300) + "…" : value;
    }

    private record DiveraCredentials(String apiBaseUrl, String accessKey, String source) {}
}
