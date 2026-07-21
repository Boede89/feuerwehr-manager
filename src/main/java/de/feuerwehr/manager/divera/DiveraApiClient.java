package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Divera 24/7 REST – {@code GET /api/v2/alarms?accesskey=…} (nicht archivierte Alarme).
 * Geschlossene Einsätze werden in {@link DiveraService} nur im Produktivbetrieb ausgeblendet.
 */
@Service
public class DiveraApiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final DiveraAlarmRawJson diveraAlarmRawJson;

    public DiveraApiClient(ObjectMapper objectMapper, DiveraAlarmRawJson diveraAlarmRawJson) {
        this.objectMapper = objectMapper;
        this.diveraAlarmRawJson = diveraAlarmRawJson;
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(5_000);
        rf.setReadTimeout(15_000);
        this.restClient = RestClient.builder().requestFactory(rf).build();
    }

    public record RawApiResponse(boolean success, String message, String body, int httpStatus, String endpoint) {}

    /** Alle nicht archivierten Alarme von DIVERA (offen und geschlossen). */
    public DiveraAlarmsResponse fetchAlarms(String apiBaseUrl, String accessKey) {
        RawApiResponse raw = fetchRawAlarms(apiBaseUrl, accessKey);
        if (!raw.success()) {
            return DiveraAlarmsResponse.fail(raw.message());
        }
        try {
            JsonNode root = objectMapper.readTree(raw.body());
            ParsedAlarms parsed = parseAlarmItems(root.path("data"));
            parsed.alarms().sort(Comparator.comparingLong(DiveraAlarmSummary::dateEpochSeconds).reversed());
            return DiveraAlarmsResponse.ok(parsed.alarms(), parsed.rawJsonByAlarmId());
        } catch (Exception e) {
            return DiveraAlarmsResponse.fail("Divera-API: " + e.getMessage());
        }
    }

    /** Rohe JSON-Antwort von GET /api/v2/alarms (für Debug/Schnittstellen-Reiter). */
    public RawApiResponse fetchRawAlarms(String apiBaseUrl, String accessKey) {
        String key = normalizeAccessKey(accessKey);
        if (key.isEmpty()) {
            return new RawApiResponse(false, "Divera Access Key fehlt (in den Einheitseinstellungen hinterlegen)", null, 0, null);
        }
        String base = normalizeApiBase(apiBaseUrl);
        URI uri = buildAlarmsUri(base, key);
        return fetchRawGet(uri, base + "/api/v2/alarms?accesskey=…");
    }

    /** Rohe JSON-Antwort von GET /api/users (für Debug/Schnittstellen-Reiter). */
    public RawApiResponse fetchRawUsers(String apiBaseUrl, String accessKey) {
        String key = normalizeAccessKey(accessKey);
        if (key.isEmpty()) {
            return new RawApiResponse(false, "Divera Access Key fehlt (in den Einheitseinstellungen hinterlegen)", null, 0, null);
        }
        String base = normalizeApiBase(apiBaseUrl);
        URI uri = UriComponentsBuilder.fromUriString(base + "/api/users")
                .queryParam("accesskey", key)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
        return fetchRawGet(uri, base + "/api/users?accesskey=…");
    }

    private RawApiResponse fetchRawGet(URI uri, String endpointLabel) {
        try {
            String raw = restClient.get().uri(uri).retrieve().body(String.class);
            if (raw == null || raw.isBlank()) {
                return new RawApiResponse(false, "DIVERA-API: Leere Antwort", null, 200, endpointLabel);
            }
            JsonNode root = objectMapper.readTree(raw);
            if (root.path("success").asBoolean(false) == false && root.has("success")) {
                String msg = textOr(root, "message", "error", "DIVERA-API: Zugriff verweigert oder ungültiger Access Key");
                return new RawApiResponse(false, msg, raw, 200, endpointLabel);
            }
            return new RawApiResponse(true, "OK", raw, 200, endpointLabel);
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            return new RawApiResponse(
                    false,
                    "DIVERA-HTTP " + e.getStatusCode().value(),
                    body != null && !body.isBlank() ? body : null,
                    e.getStatusCode().value(),
                    endpointLabel);
        } catch (Exception e) {
            return new RawApiResponse(false, "DIVERA-API: " + e.getMessage(), null, 0, endpointLabel);
        }
    }

    /** Einzelnen Alarm inkl. Rückmeldungen ({@code GET /api/v2/alarms/{id}}). */
    public java.util.Optional<JsonNode> fetchAlarmItemById(String apiBaseUrl, String accessKey, long alarmId) {
        if (alarmId <= 0) {
            return java.util.Optional.empty();
        }
        String key = normalizeAccessKey(accessKey);
        if (key.isEmpty()) {
            return java.util.Optional.empty();
        }
        String base = normalizeApiBase(apiBaseUrl);
        URI uri = UriComponentsBuilder.fromUriString(base + "/api/v2/alarms/" + alarmId)
                .queryParam("accesskey", key)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
        RawApiResponse raw = fetchRawGet(uri, base + "/api/v2/alarms/" + alarmId + "?accesskey=…");
        if (!raw.success() || raw.body() == null || raw.body().isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(raw.body());
            JsonNode data = root.path("data");
            if (data.isObject() && !data.isNull() && data.has("id")) {
                return java.util.Optional.of(data);
            }
            return java.util.Optional.empty();
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private static String normalizeAccessKey(String accessKey) {
        return accessKey == null ? "" : accessKey.trim().replaceAll("[\\r\\n\\t\\v]+", "");
    }

    private static String normalizeApiBase(String apiBaseUrl) {
        String base = trimTrailingSlash(apiBaseUrl);
        return base.isEmpty() ? "https://app.divera247.com" : base;
    }

    private static URI buildAlarmsUri(String base, String key) {
        return UriComponentsBuilder.fromUriString(base + "/api/v2/alarms")
                .queryParam("accesskey", key)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
    }

    private record ParsedAlarms(List<DiveraAlarmSummary> alarms, Map<Long, String> rawJsonByAlarmId) {}

    private ParsedAlarms parseAlarmItems(JsonNode dataBlock) {
        List<DiveraAlarmSummary> alarms = new ArrayList<>();
        Map<Long, String> rawJsonByAlarmId = new LinkedHashMap<>();
        if (dataBlock == null || dataBlock.isNull()) {
            return new ParsedAlarms(alarms, rawJsonByAlarmId);
        }
        if (dataBlock.isArray()) {
            for (JsonNode item : dataBlock) {
                addParsedItem(item, alarms, rawJsonByAlarmId);
            }
            return new ParsedAlarms(alarms, rawJsonByAlarmId);
        }
        JsonNode items = dataBlock.path("items");
        if (items.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = items.fields();
            while (it.hasNext()) {
                addParsedItem(it.next().getValue(), alarms, rawJsonByAlarmId);
            }
            return new ParsedAlarms(alarms, rawJsonByAlarmId);
        }
        if (items.isArray()) {
            for (JsonNode item : items) {
                addParsedItem(item, alarms, rawJsonByAlarmId);
            }
            return new ParsedAlarms(alarms, rawJsonByAlarmId);
        }
        if (dataBlock.isObject() && dataBlock.has("id")) {
            addParsedItem(dataBlock, alarms, rawJsonByAlarmId);
        }
        return new ParsedAlarms(alarms, rawJsonByAlarmId);
    }

    private void addParsedItem(JsonNode item, List<DiveraAlarmSummary> alarms, Map<Long, String> rawJsonByAlarmId) {
        parseOneAlarm(item).ifPresent(summary -> {
            alarms.add(summary);
            rawJsonByAlarmId.put(summary.id(), diveraAlarmRawJson.wrapApiItemAsWebhookPayload(item));
        });
    }

    private static java.util.Optional<DiveraAlarmSummary> parseOneAlarm(JsonNode item) {
        if (item == null || !item.isObject()) {
            return java.util.Optional.empty();
        }
        long id = item.path("id").asLong(0);
        if (id <= 0) {
            return java.util.Optional.empty();
        }
        long dateTs = item.path("date").asLong(0);
        if (dateTs == 0) {
            dateTs = item.path("ts_create").asLong(0);
        }
        if (dateTs > 10_000_000_000L) {
            dateTs = dateTs / 1000;
        }
        long tsCreate = item.path("ts_create").asLong(dateTs);
        if (tsCreate > 10_000_000_000L) {
            tsCreate = tsCreate / 1000;
        }
        boolean closed = DiveraAlarmClosedSupport.isClosed(item);
        return java.util.Optional.of(new DiveraAlarmSummary(
                id,
                item.path("title").asText(""),
                item.path("text").asText(""),
                item.path("address").asText(""),
                dateTs,
                tsCreate,
                closed));
    }

    private static String textOr(JsonNode n, String a, String b, String def) {
        String x = n.path(a).asText("");
        if (!x.isEmpty()) {
            return x;
        }
        x = n.path(b).asText("");
        return !x.isEmpty() ? x : def;
    }

    /** UCR-ID → aktuelle Status-ID aus DIVERA (/api/users). */
    public Map<Long, Integer> fetchUserStatusByUcr(String apiBaseUrl, String accessKey) {
        return fetchUserDirectory(apiBaseUrl, accessKey).statusByUcr();
    }

    /** DIVERA-Benutzer mit UCR, Status und Anzeigenamen. */
    public DiveraUserDirectory fetchUserDirectory(String apiBaseUrl, String accessKey) {
        String key = accessKey == null ? "" : accessKey.trim().replaceAll("[\\r\\n\\t\\v]+", "");
        if (key.isEmpty()) {
            return DiveraUserDirectory.empty();
        }
        String base = trimTrailingSlash(apiBaseUrl);
        if (base.isEmpty()) {
            base = "https://app.divera247.com";
        }
        URI uri = UriComponentsBuilder.fromUriString(base + "/api/users")
                .queryParam("accesskey", key)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
        try {
            String raw = restClient.get().uri(uri).retrieve().body(String.class);
            if (raw == null || raw.isBlank()) {
                return DiveraUserDirectory.empty();
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return DiveraUserDirectory.empty();
            }
            Map<Long, Integer> statusByUcr = new HashMap<>();
            Map<Long, String> displayNameByUcr = new LinkedHashMap<>();
            for (JsonNode user : data) {
                long ucrId = user.path("user_cluster_relation_id").asLong(0);
                if (ucrId <= 0) {
                    continue;
                }
                int statusId = user.path("status_id").asInt(0);
                if (statusId > 0) {
                    statusByUcr.put(ucrId, statusId);
                }
                String displayName = displayNameFromUser(user);
                if (displayName != null && !displayName.isBlank()) {
                    displayNameByUcr.put(ucrId, displayName);
                }
            }
            return new DiveraUserDirectory(statusByUcr, displayNameByUcr);
        } catch (Exception e) {
            return DiveraUserDirectory.empty();
        }
    }

    private static String displayNameFromUser(JsonNode user) {
        if (user == null || user.isNull()) {
            return null;
        }
        String first = textField(user, "firstname", "first_name", "Firstname");
        String last = textField(user, "lastname", "last_name", "Lastname");
        if (first != null && last != null) {
            return (first + " " + last).trim();
        }
        if (first != null) {
            return first;
        }
        if (last != null) {
            return last;
        }
        return textField(user, "name", "display_name", "fullname", "full_name", "user_name");
    }

    private static String textField(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = node.path(key).asText("");
            if (!value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String t = url.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    public record DiveraMutationResult(boolean success, String message, String body, int httpStatus) {}

    /** Termin in DIVERA anlegen ({@code POST /api/v2/events}). */
    public DiveraMutationResult createEvent(String apiBaseUrl, String accessKey, JsonNode body) {
        String key = normalizeAccessKey(accessKey);
        if (key.isEmpty()) {
            return new DiveraMutationResult(false, "Divera Access Key fehlt", null, 0);
        }
        String base = normalizeApiBase(apiBaseUrl);
        URI uri = UriComponentsBuilder.fromUriString(base + "/api/v2/events")
                .queryParam("accesskey", key)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
        return postJson(uri, body, base + "/api/v2/events?accesskey=…");
    }

    /** Termin in DIVERA löschen ({@code DELETE /api/v2/events/{id}}). */
    public DiveraMutationResult deleteEvent(String apiBaseUrl, String accessKey, long eventId) {
        if (eventId <= 0) {
            return new DiveraMutationResult(false, "Ungültige Event-ID", null, 0);
        }
        String key = normalizeAccessKey(accessKey);
        if (key.isEmpty()) {
            return new DiveraMutationResult(false, "Divera Access Key fehlt", null, 0);
        }
        String base = normalizeApiBase(apiBaseUrl);
        URI uri = UriComponentsBuilder.fromUriString(base + "/api/v2/events/" + eventId)
                .queryParam("accesskey", key)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
        try {
            restClient.delete().uri(uri).retrieve().toBodilessEntity();
            return new DiveraMutationResult(true, "OK", null, 204);
        } catch (RestClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            return new DiveraMutationResult(
                    false,
                    "DIVERA-HTTP " + e.getStatusCode().value(),
                    responseBody != null && !responseBody.isBlank() ? responseBody : null,
                    e.getStatusCode().value());
        } catch (Exception e) {
            return new DiveraMutationResult(false, "DIVERA-API: " + e.getMessage(), null, 0);
        }
    }

    private DiveraMutationResult postJson(URI uri, JsonNode body, String endpointLabel) {
        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            String raw = restClient
                    .post()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);
            return new DiveraMutationResult(true, "OK", raw, 200);
        } catch (RestClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            return new DiveraMutationResult(
                    false,
                    "DIVERA-HTTP " + e.getStatusCode().value(),
                    responseBody != null && !responseBody.isBlank() ? responseBody : null,
                    e.getStatusCode().value());
        } catch (Exception e) {
            return new DiveraMutationResult(false, "DIVERA-API: " + e.getMessage(), null, 0);
        }
    }
}
