package de.feuerwehr.manager.reservierungen;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import de.feuerwehr.manager.unit.UnitCalendarAccount;
import de.feuerwehr.manager.unit.UnitCalendarAccountRepository;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public void syncVehicleReservation(
            long unitId, VehicleReservation reservation, List<Long> calendarAccountIds) {
        syncReservation(
                unitId,
                calendarAccountIds,
                ReservationKind.VEHICLE,
                reservation.getId(),
                reservation.getVehicle().getName() + " - " + reservation.getReason(),
                reservation.getReason(),
                reservation.getLocation(),
                reservation.getStartAt(),
                reservation.getEndAt());
    }

    public void syncRoomReservation(long unitId, RoomReservation reservation, List<Long> calendarAccountIds) {
        syncReservation(
                unitId,
                calendarAccountIds,
                ReservationKind.ROOM,
                reservation.getId(),
                reservation.getRoom().getName() + " - " + reservation.getReason(),
                reservation.getReason(),
                reservation.getLocation(),
                reservation.getStartAt(),
                reservation.getEndAt());
    }

    public void deleteReservationCalendarEvent(ReservationKind kind, long reservationId) {
        List<ReservationCalendarEvent> links =
                calendarEventRepository.findAllByReservationKindAndReservationId(kind, reservationId);
        for (ReservationCalendarEvent link : links) {
            resolveCredentialsForAccount(link.getUnit().getId(), link.getCalendarAccountId())
                    .ifPresent(cred -> deleteGoogleEvent(cred, link.getGoogleEventId()));
            calendarEventRepository.delete(link);
        }
    }

    private void syncReservation(
            long unitId,
            List<Long> selectedAccountIds,
            ReservationKind kind,
            long reservationId,
            String title,
            String description,
            String location,
            Instant startAt,
            Instant endAt) {
        List<CalendarCredentials> targets = resolveCredentials(unitId, selectedAccountIds);
        if (targets.isEmpty()) {
            return;
        }
        Map<String, Object> body = Map.of(
                "summary", title,
                "description", description != null ? description : "",
                "location", location != null ? location : "",
                "start", Map.of("dateTime", RFC3339.format(startAt), "timeZone", "Europe/Berlin"),
                "end", Map.of("dateTime", RFC3339.format(endAt), "timeZone", "Europe/Berlin"));
        for (CalendarCredentials cred : targets) {
            createOrUpdateEvent(cred, kind, reservationId, body);
        }
    }

    private void createOrUpdateEvent(
            CalendarCredentials cred, ReservationKind kind, long reservationId, Map<String, Object> body) {
        try {
            RestClient client = buildClient(cred.accessToken());
            String raw = client
                    .post()
                    .uri("https://www.googleapis.com/calendar/v3/calendars/"
                            + encodeCalendarId(cred.calendarId())
                            + "/events")
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            String googleEventId = extractEventId(raw);
            if (googleEventId == null) {
                log.warn(
                        "Google-Kalender: Event-ID konnte nicht gelesen werden (Reservierung {}, Kalender {}).",
                        reservationId,
                        cred.account().getId());
                return;
            }
            ReservationCalendarEvent link = calendarEventRepository
                    .findByReservationKindAndReservationIdAndCalendarAccountId(
                            kind, reservationId, cred.account().getId())
                    .orElseGet(ReservationCalendarEvent::new);
            if (link.getUnit() == null) {
                link.setUnit(cred.account().getUnit());
            }
            link.setReservationKind(kind);
            link.setReservationId(reservationId);
            link.setCalendarAccountId(cred.account().getId());
            link.setGoogleEventId(googleEventId);
            calendarEventRepository.save(link);
        } catch (RestClientResponseException e) {
            log.warn(
                    "Google-Kalender-Sync fehlgeschlagen (Reservierung {}, Kalender {}): HTTP {} – {}",
                    reservationId,
                    cred.account().getId(),
                    e.getStatusCode().value(),
                    e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn(
                    "Google-Kalender-Sync fehlgeschlagen (Reservierung {}, Kalender {}): {}",
                    reservationId,
                    cred.account().getId(),
                    e.getMessage());
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
            toCredentials(account).ifPresent(result::add);
            if (selected == null && !result.isEmpty()) {
                // Rückwärtskompatibel: ohne Auswahl nur den ersten nutzbaren Kalender
                break;
            }
        }
        return result;
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
        if (!account.isEnabled()) {
            return java.util.Optional.empty();
        }
        if (account.getServiceAccountJson() == null || account.getServiceAccountJson().isBlank()) {
            return java.util.Optional.empty();
        }
        if (account.getCalendarId() == null || account.getCalendarId().isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            GoogleCredentials credentials = ServiceAccountCredentials.fromStream(
                            new ByteArrayInputStream(
                                    account.getServiceAccountJson().getBytes(StandardCharsets.UTF_8)))
                    .createScoped(List.of(CALENDAR_SCOPE));
            credentials.refreshIfExpired();
            return java.util.Optional.of(new CalendarCredentials(
                    account, account.getCalendarId().trim(), credentials.getAccessToken().getTokenValue()));
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
        return URI.create("https://dummy/" + calendarId.replace("@", "%40")).getRawPath().substring(1);
    }

    private static String extractEventId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int idx = raw.indexOf("\"id\"");
        if (idx < 0) {
            return null;
        }
        int start = raw.indexOf('"', idx + 4);
        if (start < 0) {
            return null;
        }
        int end = raw.indexOf('"', start + 1);
        if (end < 0) {
            return null;
        }
        return raw.substring(start + 1, end);
    }

    private record CalendarCredentials(UnitCalendarAccount account, String calendarId, String accessToken) {}
}
