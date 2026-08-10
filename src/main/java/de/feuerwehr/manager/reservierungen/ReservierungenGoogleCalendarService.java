package de.feuerwehr.manager.reservierungen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import de.feuerwehr.manager.unit.UnitCalendarAccount;
import de.feuerwehr.manager.unit.UnitCalendarAccountRepository;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservierungenGoogleCalendarService {

    private static final String CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar";
    private static final DateTimeFormatter RFC3339 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(ZoneId.of("Europe/Berlin"));

    private final UnitCalendarAccountRepository calendarAccountRepository;
    private final ReservationCalendarEventRepository calendarEventRepository;
    private final ObjectMapper objectMapper;

    public int syncVehicleReservation(
            long unitId, VehicleReservation reservation, List<Long> calendarAccountIds) {
        return syncVehicleReservation(unitId, reservation, calendarAccountIds, null, Map.of());
    }

    /**
     * @param combinedResourceNames Fahrzeugtitel für mehrere Reservierungen; null = nur diese
     * @param existingGoogleEventIdByAccountId bestehende Google-Event-IDs je Kalenderkonto (Merge)
     */
    public int syncVehicleReservation(
            long unitId,
            VehicleReservation reservation,
            List<Long> calendarAccountIds,
            String combinedResourceNames,
            Map<Long, String> existingGoogleEventIdByAccountId) {
        String resource =
                combinedResourceNames != null && !combinedResourceNames.isBlank()
                        ? combinedResourceNames
                        : reservation.vehicleNamesJoined();
        return syncReservation(
                unitId,
                calendarAccountIds,
                ReservationKind.VEHICLE,
                reservation.getId(),
                resource + " - " + reservation.getReason(),
                reservation.getReason(),
                reservation.getLocation(),
                reservation.getStartAt(),
                reservation.getEndAt(),
                existingGoogleEventIdByAccountId != null ? existingGoogleEventIdByAccountId : Map.of());
    }

    public int syncRoomReservation(long unitId, RoomReservation reservation, List<Long> calendarAccountIds) {
        return syncReservation(
                unitId,
                calendarAccountIds,
                ReservationKind.ROOM,
                reservation.getId(),
                reservation.getRoom().getName() + " - " + reservation.getReason(),
                reservation.getReason(),
                reservation.getLocation(),
                reservation.getStartAt(),
                reservation.getEndAt(),
                Map.of());
    }

    public void deleteReservationCalendarEvent(ReservationKind kind, long reservationId) {
        deleteReservationCalendarEvent(kind, reservationId, true);
    }

    /**
     * @param deleteRemoteIfUnshared wenn false, nur DB-Link entfernen (Termin bleibt für andere Reservierungen)
     */
    public void deleteReservationCalendarEvent(ReservationKind kind, long reservationId, boolean deleteRemoteIfUnshared) {
        List<ReservationCalendarEvent> links =
                calendarEventRepository.findAllByReservationKindAndReservationId(kind, reservationId);
        for (ReservationCalendarEvent link : links) {
            boolean shared = false;
            if (deleteRemoteIfUnshared && link.getGoogleEventId() != null) {
                List<ReservationCalendarEvent> sameEvent =
                        calendarEventRepository.findByReservationKindAndGoogleEventIdAndCalendarAccountId(
                                kind, link.getGoogleEventId(), link.getCalendarAccountId());
                shared = sameEvent.stream().anyMatch(other -> other.getReservationId() != reservationId);
            }
            if (deleteRemoteIfUnshared && !shared) {
                resolveCredentialsForAccount(link.getUnit().getId(), link.getCalendarAccountId())
                        .ifPresent(cred -> deleteGoogleEvent(cred, link.getGoogleEventId()));
            }
            calendarEventRepository.delete(link);
        }
    }

    /** Google-Event-IDs der Kalenderkonten für eine Reservierung. */
    public Map<Long, String> googleEventIdsByAccount(ReservationKind kind, long reservationId) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (ReservationCalendarEvent link :
                calendarEventRepository.findAllByReservationKindAndReservationId(kind, reservationId)) {
            if (link.getCalendarAccountId() != null
                    && link.getGoogleEventId() != null
                    && !link.getGoogleEventId().isBlank()) {
                map.putIfAbsent(link.getCalendarAccountId(), link.getGoogleEventId());
            }
        }
        return map;
    }

    private int syncReservation(
            long unitId,
            List<Long> selectedAccountIds,
            ReservationKind kind,
            long reservationId,
            String title,
            String description,
            String location,
            Instant startAt,
            Instant endAt,
            Map<Long, String> existingGoogleEventIdByAccountId) {
        List<CalendarCredentials> targets = resolveCredentials(unitId, selectedAccountIds);
        if (targets.isEmpty()) {
            log.warn(
                    "Google-Kalender-Sync übersprungen (Reservierung {}): kein nutzbarer Kalender für Einheit {}"
                            + " (aktiviert, Calendar-ID und Service-Account-JSON prüfen; Kalender mit"
                            + " client_email teilen).",
                    reservationId,
                    unitId);
            return 0;
        }
        String jsonBody = buildEventJson(title, description, location, startAt, endAt);
        if (jsonBody == null) {
            return 0;
        }

        int created = 0;
        for (CalendarCredentials cred : targets) {
            String existingId = existingGoogleEventIdByAccountId.get(cred.account().getId());
            if (createOrUpdateEvent(cred, kind, reservationId, jsonBody, existingId)) {
                created++;
            }
        }
        return created;
    }

    private String buildEventJson(
            String title, String description, String location, Instant startAt, Instant endAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", title);
        body.put("description", description != null ? description : "");
        body.put("location", location != null ? location : "");
        Map<String, Object> start = new HashMap<>();
        start.put("dateTime", RFC3339.format(startAt));
        start.put("timeZone", "Europe/Berlin");
        Map<String, Object> end = new HashMap<>();
        end.put("dateTime", RFC3339.format(endAt));
        end.put("timeZone", "Europe/Berlin");
        body.put("start", start);
        body.put("end", end);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.warn("Google-Kalender: Event-JSON konnte nicht erzeugt werden: {}", e.getMessage());
            return null;
        }
    }

    private boolean createOrUpdateEvent(
            CalendarCredentials cred,
            ReservationKind kind,
            long reservationId,
            String jsonBody,
            String preferGoogleEventId) {
        try {
            ReservationCalendarEvent existingLink = calendarEventRepository
                    .findByReservationKindAndReservationIdAndCalendarAccountId(
                            kind, reservationId, cred.account().getId())
                    .orElse(null);
            String updateId = preferGoogleEventId;
            if ((updateId == null || updateId.isBlank()) && existingLink != null) {
                updateId = existingLink.getGoogleEventId();
            }

            RestClient client = buildClient(cred.accessToken());
            String googleEventId;
            if (updateId != null && !updateId.isBlank()) {
                if (!updateGoogleEvent(cred, updateId, jsonBody)) {
                    return false;
                }
                googleEventId = updateId;
                log.info(
                        "Google-Kalender-Termin {} für Reservierung {} in Kalender {} aktualisiert.",
                        googleEventId,
                        reservationId,
                        cred.account().getId());
            } else {
                String raw = client
                        .post()
                        .uri("https://www.googleapis.com/calendar/v3/calendars/"
                                + encodeCalendarId(cred.calendarId())
                                + "/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(jsonBody)
                        .retrieve()
                        .body(String.class);
                googleEventId = extractEventId(raw);
                if (googleEventId == null) {
                    log.warn(
                            "Google-Kalender: Event-ID konnte nicht gelesen werden (Reservierung {}, Kalender {})."
                                    + " Body={}",
                            reservationId,
                            cred.account().getId(),
                            abbreviate(raw));
                    return false;
                }
                log.info(
                        "Google-Kalender-Termin {} für Reservierung {} in Kalender {} angelegt.",
                        googleEventId,
                        reservationId,
                        cred.account().getId());
            }

            ReservationCalendarEvent link = existingLink != null ? existingLink : new ReservationCalendarEvent();
            if (link.getUnit() == null) {
                link.setUnit(cred.account().getUnit());
            }
            link.setReservationKind(kind);
            link.setReservationId(reservationId);
            link.setCalendarAccountId(cred.account().getId());
            link.setGoogleEventId(googleEventId);
            calendarEventRepository.save(link);
            return true;
        } catch (RestClientResponseException e) {
            log.warn(
                    "Google-Kalender-Sync fehlgeschlagen (Reservierung {}, Kalender {}, client_email={}):"
                            + " HTTP {} – {}",
                    reservationId,
                    cred.account().getId(),
                    cred.clientEmail(),
                    e.getStatusCode().value(),
                    abbreviate(e.getResponseBodyAsString()));
            return false;
        } catch (Exception e) {
            log.warn(
                    "Google-Kalender-Sync fehlgeschlagen (Reservierung {}, Kalender {}, client_email={}): {}",
                    reservationId,
                    cred.account().getId(),
                    cred.clientEmail(),
                    e.getMessage());
            return false;
        }
    }

    private boolean updateGoogleEvent(CalendarCredentials cred, String googleEventId, String jsonBody) {
        try {
            RestClient client = buildClient(cred.accessToken());
            client.put()
                    .uri("https://www.googleapis.com/calendar/v3/calendars/"
                            + encodeCalendarId(cred.calendarId())
                            + "/events/"
                            + encodeCalendarId(googleEventId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            log.warn(
                    "Google-Kalender-Update fehlgeschlagen (Event {}, Kalender {}): HTTP {} – {}",
                    googleEventId,
                    cred.account().getId(),
                    e.getStatusCode().value(),
                    abbreviate(e.getResponseBodyAsString()));
            return false;
        } catch (Exception e) {
            log.warn(
                    "Google-Kalender-Update fehlgeschlagen (Event {}, Kalender {}): {}",
                    googleEventId,
                    cred.account().getId(),
                    e.getMessage());
            return false;
        }
    }

    private void deleteGoogleEvent(CalendarCredentials cred, String googleEventId) {
        if (googleEventId == null || googleEventId.isBlank()) {
            return;
        }
        try {
            RestClient client = buildClient(cred.accessToken());
            client.delete()
                    .uri("https://www.googleapis.com/calendar/v3/calendars/"
                            + encodeCalendarId(cred.calendarId())
                            + "/events/"
                            + encodeCalendarId(googleEventId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Google-Kalender-Event {} konnte nicht gelöscht werden: {}", googleEventId, e.getMessage());
        }
    }

    private List<CalendarCredentials> resolveCredentials(long unitId, List<Long> selectedAccountIds) {
        List<UnitCalendarAccount> accounts = calendarAccountRepository.findByUnitIdOrderBySortOrderAscLabelAsc(unitId);
        Set<Long> selected = selectedAccountIds == null || selectedAccountIds.isEmpty()
                ? null
                : new HashSet<>(selectedAccountIds);
        List<CalendarCredentials> result = new ArrayList<>();
        for (UnitCalendarAccount account : accounts) {
            if (selected != null && !selected.contains(account.getId())) {
                continue;
            }
            toCredentials(account).ifPresentOrElse(result::add, () -> logSkipReason(account, selected != null));
        }
        return result;
    }

    private void logSkipReason(UnitCalendarAccount account, boolean wasSelected) {
        if (account.getServiceAccountJson() == null || account.getServiceAccountJson().isBlank()) {
            log.warn("Google-Kalender-Konto {} übersprungen: kein Service-Account-JSON.", account.getId());
            return;
        }
        if (account.getCalendarId() == null || account.getCalendarId().isBlank()) {
            log.warn("Google-Kalender-Konto {} übersprungen: keine Calendar-ID.", account.getId());
            return;
        }
        if (wasSelected) {
            log.warn(
                    "Google-Kalender-Konto {} übersprungen: Zugangsdaten ungültig (JSON/Token).",
                    account.getId());
        }
    }

    private java.util.Optional<CalendarCredentials> resolveCredentialsForAccount(long unitId, Long accountId) {
        if (accountId == null) {
            List<CalendarCredentials> fallback = resolveCredentials(unitId, List.of());
            return fallback.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(fallback.get(0));
        }
        return calendarAccountRepository
                .findById(accountId)
                .filter(a -> a.getUnit() != null && a.getUnit().getId().equals(unitId))
                .flatMap(this::toCredentials);
    }

    private java.util.Optional<CalendarCredentials> toCredentials(UnitCalendarAccount account) {
        if (account.getServiceAccountJson() == null || account.getServiceAccountJson().isBlank()) {
            return java.util.Optional.empty();
        }
        if (account.getCalendarId() == null || account.getCalendarId().isBlank()) {
            return java.util.Optional.empty();
        }
        if (!account.isEnabled()) {
            log.warn(
                    "Google-Kalender-Konto {} ist deaktiviert – Sync wird trotzdem versucht"
                            + " (bitte unter Schnittstellen auf „Aktiv“ setzen).",
                    account.getId());
        }
        try {
            ServiceAccountCredentials serviceAccount = ServiceAccountCredentials.fromStream(
                    new ByteArrayInputStream(account.getServiceAccountJson().getBytes(StandardCharsets.UTF_8)));
            GoogleCredentials credentials = serviceAccount.createScoped(List.of(CALENDAR_SCOPE));
            credentials.refresh();
            if (credentials.getAccessToken() == null || credentials.getAccessToken().getTokenValue() == null) {
                log.warn("Google-Kalender-Zugang für Konto {}: Access Token leer nach refresh.", account.getId());
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new CalendarCredentials(
                    account,
                    account.getCalendarId().trim(),
                    credentials.getAccessToken().getTokenValue(),
                    serviceAccount.getClientEmail()));
        } catch (Exception e) {
            log.warn(
                    "Google-Kalender-Zugang für Konto {} nicht nutzbar: {}",
                    account.getId(),
                    e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private RestClient buildClient(String accessToken) {
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(5_000);
        rf.setReadTimeout(15_000);
        return RestClient.builder()
                .requestFactory(rf)
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();
    }

    private static String encodeCalendarId(String calendarId) {
        return URLEncoder.encode(calendarId, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String extractEventId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            String id = root.path("id").asText(null);
            return id != null && !id.isBlank() ? id : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 400 ? value.substring(0, 400) + "…" : value;
    }

    private record CalendarCredentials(
            UnitCalendarAccount account, String calendarId, String accessToken, String clientEmail) {}
}
