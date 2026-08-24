package de.feuerwehr.manager.reservierungen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import de.feuerwehr.manager.unit.UnitCalendarAccount;
import de.feuerwehr.manager.unit.UnitCalendarAccountRepository;
import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern ICAL_ID_PATTERN = Pattern.compile(
            "/calendar/ical/([^/]+)/(?:public|private-[^/]+)/basic\\.ics", Pattern.CASE_INSENSITIVE);

    private final UnitCalendarAccountRepository calendarAccountRepository;
    private final ReservationCalendarEventRepository calendarEventRepository;
    private final ObjectMapper objectMapper;

    public record SyncResult(int synced, List<String> errors) {
        public boolean ok() {
            return synced > 0;
        }

        public String primaryError() {
            return errors == null || errors.isEmpty() ? null : errors.get(0);
        }
    }

    public int syncVehicleReservation(
            long unitId, VehicleReservation reservation, List<Long> calendarAccountIds) {
        return syncVehicleReservation(unitId, reservation, calendarAccountIds, null, Map.of()).synced();
    }

    /**
     * @param combinedResourceNames Fahrzeugtitel für mehrere Reservierungen; null = nur diese
     * @param existingGoogleEventIdByAccountId bestehende Google-Event-IDs je Kalenderkonto (Merge)
     */
    public SyncResult syncVehicleReservation(
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

    public SyncResult syncRoomReservation(long unitId, RoomReservation reservation, List<Long> calendarAccountIds) {
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

    /** Kurztest: Token + Kalender lesbar (ohne Termin anzulegen). */
    public String testCalendarAccess(UnitCalendarAccount account) {
        String calendarId = resolveCalendarId(account);
        if (calendarId == null || calendarId.isBlank()) {
            return "Keine Calendar-ID hinterlegt – in Google Kalender unter Einstellungen →"
                    + " Integrationsadresse die Kalender-ID kopieren (z. B. …@group.calendar.google.com)"
                    + " und hier eintragen (nicht nur die iCal-URL).";
        }
        return toCredentials(account, true)
                .map(cred -> {
                    RestClient client = buildClient(cred.accessToken());
                    try {
                        String raw = client
                                .get()
                                .uri("https://www.googleapis.com/calendar/v3/calendars/"
                                        + encodeCalendarId(cred.calendarId()))
                                .retrieve()
                                .body(String.class);
                        String summary = null;
                        if (raw != null && !raw.isBlank()) {
                            summary = objectMapper.readTree(raw).path("summary").asText(null);
                        }
                        String label = summary != null && !summary.isBlank() ? summary : cred.calendarId();
                        return "OK – Kalender „"
                                + label
                                + "“ erreichbar (Calendar-ID: "
                                + cred.calendarId()
                                + ", client_email="
                                + cred.clientEmail()
                                + ").";
                    } catch (RestClientResponseException e) {
                        String base = "Fehler HTTP "
                                + e.getStatusCode().value()
                                + ": "
                                + humanizeGoogleError(
                                        e.getResponseBodyAsString(), cred.clientEmail(), cred.calendarId());
                        if (e.getStatusCode().value() == 404) {
                            base += " " + describeVisibleCalendars(client, cred.clientEmail());
                        }
                        return base;
                    } catch (Exception e) {
                        return "Fehler: " + e.getMessage();
                    }
                })
                .orElseGet(() -> describeMissingCredentials(account) + " (Calendar-ID: " + calendarId + ").");
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

    private SyncResult syncReservation(
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
        List<String> errors = new ArrayList<>();
        List<CalendarCredentials> targets = resolveCredentials(unitId, selectedAccountIds, errors);
        if (targets.isEmpty()) {
            if (errors.isEmpty()) {
                errors.add(
                        "kein nutzbarer Kalender (Aktiv + Calendar-ID + Service-Account-JSON;"
                                + " unter Reservierungen → Einstellungen auswählen).");
            }
            log.warn(
                    "Google-Kalender-Sync übersprungen (Reservierung {}, Einheit {}): {}",
                    reservationId,
                    unitId,
                    String.join("; ", errors));
            return new SyncResult(0, List.copyOf(errors));
        }

        Map<String, Object> eventBody = buildEventBody(title, description, location, startAt, endAt);
        int synced = 0;
        for (CalendarCredentials cred : targets) {
            String existingId = existingGoogleEventIdByAccountId.get(cred.account().getId());
            String err = createOrUpdateEvent(cred, kind, reservationId, eventBody, existingId);
            if (err == null) {
                synced++;
            } else {
                errors.add(err);
            }
        }
        return new SyncResult(synced, List.copyOf(errors));
    }

    private Map<String, Object> buildEventBody(
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
        return body;
    }

    /** @return null bei Erfolg, sonst kurze Fehlermeldung */
    private String createOrUpdateEvent(
            CalendarCredentials cred,
            ReservationKind kind,
            long reservationId,
            Map<String, Object> eventBody,
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
                String updateErr = updateGoogleEvent(cred, updateId, eventBody);
                if (updateErr != null) {
                    // Veraltete Event-ID → neu anlegen statt abbrechen
                    if (updateErr.contains("HTTP 404")) {
                        log.info(
                                "Google-Kalender: Event {} fehlt – lege neu an (Reservierung {}, Kalender {}).",
                                updateId,
                                reservationId,
                                cred.account().getId());
                        googleEventId = insertGoogleEvent(client, cred, eventBody);
                    } else {
                        log.warn(
                                "Google-Kalender-Update fehlgeschlagen (Reservierung {}, Kalender {},"
                                        + " client_email={}): {}",
                                reservationId,
                                cred.account().getId(),
                                cred.clientEmail(),
                                updateErr);
                        return kalenderLabel(cred) + ": " + updateErr;
                    }
                } else {
                    googleEventId = updateId;
                    log.info(
                            "Google-Kalender-Termin {} für Reservierung {} in Kalender {} aktualisiert.",
                            googleEventId,
                            reservationId,
                            cred.account().getId());
                }
            } else {
                googleEventId = insertGoogleEvent(client, cred, eventBody);
                log.info(
                        "Google-Kalender-Termin {} für Reservierung {} in Kalender {} angelegt.",
                        googleEventId,
                        reservationId,
                        cred.account().getId());
            }

            if (googleEventId == null || googleEventId.isBlank()) {
                return kalenderLabel(cred) + ": Event-ID konnte nicht gelesen werden.";
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
            return null;
        } catch (RestClientResponseException e) {
            String detail = humanizeGoogleError(e.getResponseBodyAsString(), cred.clientEmail(), cred.calendarId());
            log.warn(
                    "Google-Kalender-Sync fehlgeschlagen (Reservierung {}, Kalender {}, client_email={}):"
                            + " HTTP {} – {}",
                    reservationId,
                    cred.account().getId(),
                    cred.clientEmail(),
                    e.getStatusCode().value(),
                    abbreviate(e.getResponseBodyAsString()));
            return kalenderLabel(cred) + ": HTTP " + e.getStatusCode().value() + " – " + detail;
        } catch (Exception e) {
            log.warn(
                    "Google-Kalender-Sync fehlgeschlagen (Reservierung {}, Kalender {}, client_email={}): {}",
                    reservationId,
                    cred.account().getId(),
                    cred.clientEmail(),
                    e.getMessage());
            return kalenderLabel(cred) + ": " + e.getMessage();
        }
    }

    private String insertGoogleEvent(RestClient client, CalendarCredentials cred, Map<String, Object> eventBody) {
        String raw = client
                .post()
                .uri("https://www.googleapis.com/calendar/v3/calendars/"
                        + encodeCalendarId(cred.calendarId())
                        + "/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(eventBody)
                .retrieve()
                .body(String.class);
        return extractEventId(raw);
    }

    /** @return null bei Erfolg */
    private String updateGoogleEvent(CalendarCredentials cred, String googleEventId, Map<String, Object> eventBody) {
        try {
            RestClient client = buildClient(cred.accessToken());
            client.put()
                    .uri("https://www.googleapis.com/calendar/v3/calendars/"
                            + encodeCalendarId(cred.calendarId())
                            + "/events/"
                            + encodeCalendarId(googleEventId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(eventBody)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        } catch (RestClientResponseException e) {
            return "HTTP " + e.getStatusCode().value() + " – "
                    + humanizeGoogleError(e.getResponseBodyAsString(), cred.clientEmail(), cred.calendarId());
        } catch (Exception e) {
            return e.getMessage() != null ? e.getMessage() : "Update fehlgeschlagen";
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

    private List<CalendarCredentials> resolveCredentials(
            long unitId, List<Long> selectedAccountIds, List<String> errors) {
        List<UnitCalendarAccount> accounts = calendarAccountRepository.findByUnitIdOrderBySortOrderAscLabelAsc(unitId);
        Set<Long> selected = selectedAccountIds == null || selectedAccountIds.isEmpty()
                ? null
                : new HashSet<>(selectedAccountIds);
        List<CalendarCredentials> result = new ArrayList<>();
        int considered = 0;
        for (UnitCalendarAccount account : accounts) {
            if (selected != null && !selected.contains(account.getId())) {
                continue;
            }
            considered++;
            toCredentials(account, false).ifPresentOrElse(result::add, () -> {
                String reason = describeMissingCredentials(account);
                errors.add((account.getLabel() != null && !account.getLabel().isBlank()
                                ? account.getLabel()
                                : "Kalender #" + account.getId())
                        + ": " + reason);
            });
        }
        if (considered == 0) {
            if (selected != null) {
                errors.add("ausgewählte Kalender-IDs nicht gefunden – Einstellungen prüfen.");
            } else {
                errors.add("kein Google-Kalender-Konto für diese Einheit hinterlegt.");
            }
        }
        return result;
    }

    private java.util.Optional<CalendarCredentials> resolveCredentialsForAccount(long unitId, Long accountId) {
        if (accountId == null) {
            List<String> ignored = new ArrayList<>();
            List<CalendarCredentials> fallback = resolveCredentials(unitId, List.of(), ignored);
            return fallback.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(fallback.get(0));
        }
        return calendarAccountRepository
                .findById(accountId)
                .filter(a -> a.getUnit() != null && a.getUnit().getId().equals(unitId))
                .flatMap(a -> toCredentials(a, false));
    }

    private java.util.Optional<CalendarCredentials> toCredentials(UnitCalendarAccount account, boolean forTest) {
        if (!account.isEnabled() && !forTest) {
            return java.util.Optional.empty();
        }
        String calendarId = resolveCalendarId(account);
        if (account.getServiceAccountJson() == null || account.getServiceAccountJson().isBlank()) {
            return java.util.Optional.empty();
        }
        if (calendarId == null || calendarId.isBlank()) {
            return java.util.Optional.empty();
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
                    calendarId,
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

    static String resolveCalendarId(UnitCalendarAccount account) {
        return normalizeCalendarIdInput(account.getCalendarId(), account.getCalendarUrl());
    }

    /** iCal-URL oder komplette Google-URL in reine Calendar-ID umwandeln. */
    public static String normalizeCalendarIdInput(String calendarId, String calendarUrl) {
        String id = calendarId != null ? calendarId.trim() : null;
        if (id != null && id.isEmpty()) {
            id = null;
        }
        if (id != null
                && (id.contains("calendar.google.com") || id.startsWith("http://") || id.startsWith("https://"))) {
            String extracted = extractCalendarIdFromIcalUrl(id);
            if (extracted != null && !extracted.isBlank()) {
                return extracted.trim();
            }
        }
        if (id != null) {
            return id;
        }
        return extractCalendarIdFromIcalUrl(calendarUrl);
    }

    /** z. B. …/calendar/ical/xxx%40group.calendar.google.com/public/basic.ics */
    public static String extractCalendarIdFromIcalUrl(String calendarUrl) {
        if (calendarUrl == null || calendarUrl.isBlank()) {
            return null;
        }
        Matcher m = ICAL_ID_PATTERN.matcher(calendarUrl.trim());
        if (!m.find()) {
            return null;
        }
        try {
            String decoded = URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
            return decoded != null && !decoded.isBlank() ? decoded.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String describeMissingCredentials(UnitCalendarAccount account) {
        if (account == null) {
            return "Kalender nicht gefunden.";
        }
        if (!account.isEnabled()) {
            return "Kalender ist nicht aktiv (unter Schnittstellen „Aktiv“ setzen).";
        }
        if (account.getServiceAccountJson() == null || account.getServiceAccountJson().isBlank()) {
            return "kein Service-Account-JSON hinterlegt.";
        }
        if (resolveCalendarId(account) == null) {
            return "keine Calendar-ID (und iCal-URL enthält keine erkennbare ID).";
        }
        return "Service-Account-JSON ungültig oder Token konnte nicht erzeugt werden.";
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

    /** Welche Kalender der Service-Account laut API sieht (Hilfe bei 404). */
    private String describeVisibleCalendars(RestClient client, String clientEmail) {
        try {
            String raw = client
                    .get()
                    .uri("https://www.googleapis.com/calendar/v3/users/me/calendarList?maxResults=25")
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                return "Der Service-Account (" + clientEmail + ") sieht keinen Kalender – Freigabe fehlt.";
            }
            JsonNode items = objectMapper.readTree(raw).path("items");
            if (!items.isArray() || items.isEmpty()) {
                return "Der Service-Account (" + clientEmail + ") sieht keinen Kalender – unter „Für bestimmte"
                        + " Personen freigeben“ die client_email mit „Termine ändern“ hinzufügen.";
            }
            List<String> parts = new ArrayList<>();
            for (JsonNode item : items) {
                String id = item.path("id").asText("");
                String summary = item.path("summary").asText("");
                if (id.isBlank()) {
                    continue;
                }
                parts.add((summary.isBlank() ? id : summary + " → " + id));
                if (parts.size() >= 5) {
                    break;
                }
            }
            if (parts.isEmpty()) {
                return "Der Service-Account sieht keinen nutzbaren Kalender.";
            }
            return "Kalender, die " + clientEmail + " laut Google sieht: "
                    + String.join("; ", parts)
                    + (items.size() > parts.size() ? " …" : "")
                    + ". Steht die Ziel-ID dabei? Wenn nein: genau diesen Kalender freigeben.";
        } catch (Exception e) {
            return "Kalenderliste des Service-Accounts konnte nicht gelesen werden: " + e.getMessage();
        }
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

    private String humanizeGoogleError(String responseBody, String clientEmail, String calendarId) {
        String apiMessage = null;
        String reason = null;
        if (responseBody != null && !responseBody.isBlank()) {
            try {
                JsonNode err = objectMapper.readTree(responseBody).path("error");
                apiMessage = err.path("message").asText(null);
                JsonNode errors = err.path("errors");
                if (errors.isArray() && !errors.isEmpty()) {
                    reason = errors.get(0).path("reason").asText(null);
                }
            } catch (Exception ignored) {
                // raw fallback below
            }
        }
        String lower = ((apiMessage != null ? apiMessage : "") + " " + (responseBody != null ? responseBody : ""))
                .toLowerCase();
        if (lower.contains("not found") || "notFound".equalsIgnoreCase(reason)) {
            String idHint = calendarId != null && !calendarId.isBlank()
                    ? "Verwendete Calendar-ID: " + calendarId + ". "
                    : "";
            return idHint
                    + "Kalender nicht gefunden – dieselbe ID wie in Google Kalender → Einstellungen →"
                    + " Integrationsadresse eintragen und genau diesen Kalender mit "
                    + (clientEmail != null ? clientEmail : "client_email")
                    + " teilen (Termine ändern).";
        }
        if (lower.contains("access not configured")
                || lower.contains("has not been used")
                || lower.contains("is disabled")
                || "accessNotConfigured".equalsIgnoreCase(reason)) {
            return "Google Calendar API im Google-Cloud-Projekt nicht aktiviert.";
        }
        if (lower.contains("forbidden") || "forbidden".equalsIgnoreCase(reason)) {
            return "Keine Schreibrechte – Kalender mit "
                    + (clientEmail != null ? clientEmail : "client_email")
                    + " teilen (Berechtigung „Termine ändern“).";
        }
        if (apiMessage != null && !apiMessage.isBlank()) {
            return apiMessage;
        }
        return abbreviate(responseBody);
    }

    private static String kalenderLabel(CalendarCredentials cred) {
        String label = cred.account().getLabel();
        if (label != null && !label.isBlank()) {
            return label;
        }
        return "Kalender #" + cred.account().getId();
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
