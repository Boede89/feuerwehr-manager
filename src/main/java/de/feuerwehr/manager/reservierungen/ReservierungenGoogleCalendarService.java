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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public void syncVehicleReservation(long unitId, VehicleReservation reservation) {
        syncReservation(
                unitId,
                ReservationKind.VEHICLE,
                reservation.getId(),
                reservation.getVehicle().getName() + " - " + reservation.getReason(),
                reservation.getReason(),
                reservation.getLocation(),
                reservation.getStartAt(),
                reservation.getEndAt());
    }

    public void syncRoomReservation(long unitId, RoomReservation reservation) {
        syncReservation(
                unitId,
                ReservationKind.ROOM,
                reservation.getId(),
                reservation.getRoom().getName() + " - " + reservation.getReason(),
                reservation.getReason(),
                reservation.getLocation(),
                reservation.getStartAt(),
                reservation.getEndAt());
    }

    public void deleteReservationCalendarEvent(ReservationKind kind, long reservationId) {
        calendarEventRepository
                .findByReservationKindAndReservationId(kind, reservationId)
                .ifPresent(link -> {
                    Optional<CalendarCredentials> credentials = resolveCredentials(link.getUnit().getId());
                    credentials.ifPresent(cred -> deleteGoogleEvent(cred, link.getGoogleEventId()));
                    calendarEventRepository.delete(link);
                });
    }

    private void syncReservation(
            long unitId,
            ReservationKind kind,
            long reservationId,
            String title,
            String description,
            String location,
            Instant startAt,
            Instant endAt) {
        Optional<CalendarCredentials> credentials = resolveCredentials(unitId);
        if (credentials.isEmpty()) {
            return;
        }
        CalendarCredentials cred = credentials.get();
        Map<String, Object> body = Map.of(
                "summary", title,
                "description", description != null ? description : "",
                "location", location != null ? location : "",
                "start", Map.of("dateTime", RFC3339.format(startAt), "timeZone", "Europe/Berlin"),
                "end", Map.of("dateTime", RFC3339.format(endAt), "timeZone", "Europe/Berlin"));
        try {
            RestClient client = buildClient(cred.accessToken());
            String raw = client
                    .post()
                    .uri("https://www.googleapis.com/calendar/v3/calendars/" + encodeCalendarId(cred.calendarId()) + "/events")
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            String googleEventId = extractEventId(raw);
            if (googleEventId == null) {
                log.warn("Google-Kalender: Event-ID konnte nicht gelesen werden (Reservierung {}).", reservationId);
                return;
            }
            ReservationCalendarEvent link = calendarEventRepository
                    .findByReservationKindAndReservationId(kind, reservationId)
                    .orElseGet(ReservationCalendarEvent::new);
            if (link.getUnit() == null) {
                link.setUnit(cred.account().getUnit());
            }
            link.setReservationKind(kind);
            link.setReservationId(reservationId);
            link.setGoogleEventId(googleEventId);
            calendarEventRepository.save(link);
        } catch (RestClientResponseException e) {
            log.warn(
                    "Google-Kalender-Sync fehlgeschlagen (Reservierung {}): HTTP {} – {}",
                    reservationId,
                    e.getStatusCode().value(),
                    e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Google-Kalender-Sync fehlgeschlagen (Reservierung {}): {}", reservationId, e.getMessage());
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

    private Optional<CalendarCredentials> resolveCredentials(long unitId) {
        List<UnitCalendarAccount> accounts = calendarAccountRepository.findByUnitIdOrderBySortOrderAscLabelAsc(unitId);
        for (UnitCalendarAccount account : accounts) {
            if (!account.isEnabled()) {
                continue;
            }
            if (account.getServiceAccountJson() == null || account.getServiceAccountJson().isBlank()) {
                continue;
            }
            if (account.getCalendarId() == null || account.getCalendarId().isBlank()) {
                continue;
            }
            try {
                GoogleCredentials credentials = ServiceAccountCredentials.fromStream(
                                new ByteArrayInputStream(account.getServiceAccountJson().getBytes(StandardCharsets.UTF_8)))
                        .createScoped(List.of(CALENDAR_SCOPE));
                credentials.refreshIfExpired();
                return Optional.of(new CalendarCredentials(account, account.getCalendarId().trim(), credentials.getAccessToken().getTokenValue()));
            } catch (Exception e) {
                log.warn("Google-Kalender-Zugang für Einheit {} nicht nutzbar: {}", unitId, e.getMessage());
            }
        }
        return Optional.empty();
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
