package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
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

    public DiveraApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(5_000);
        rf.setReadTimeout(15_000);
        this.restClient = RestClient.builder().requestFactory(rf).build();
    }

    /** Alle nicht archivierten Alarme von DIVERA (offen und geschlossen). */
    public DiveraAlarmsResponse fetchAlarms(String apiBaseUrl, String accessKey) {
        String key = accessKey == null ? "" : accessKey.trim().replaceAll("[\\r\\n\\t\\v]+", "");
        if (key.isEmpty()) {
            return DiveraAlarmsResponse.fail("Divera Access Key fehlt (in den Einheitseinstellungen hinterlegen)");
        }
        String base = trimTrailingSlash(apiBaseUrl);
        if (base.isEmpty()) {
            base = "https://app.divera247.com";
        }
        URI uri = buildAlarmsUri(base, key);

        try {
            String raw = restClient.get().uri(uri).retrieve().body(String.class);
            if (raw == null || raw.isBlank()) {
                return DiveraAlarmsResponse.fail("Divera-API: Leere Antwort");
            }
            JsonNode root = objectMapper.readTree(raw);
            if (root.path("success").asBoolean(false) == false && root.has("success")) {
                String msg = textOr(root, "message", "error", "Divera-API: Zugriff verweigert oder ungültiger Access Key");
                return DiveraAlarmsResponse.fail(msg);
            }
            List<DiveraAlarmSummary> alarms = parseAlarmItems(root.path("data"));
            alarms.sort(Comparator.comparingLong(DiveraAlarmSummary::dateEpochSeconds).reversed());
            return DiveraAlarmsResponse.ok(alarms);
        } catch (RestClientResponseException e) {
            return DiveraAlarmsResponse.fail("Divera-HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            return DiveraAlarmsResponse.fail("Divera-API: " + e.getMessage());
        }
    }

    private static URI buildAlarmsUri(String base, String key) {
        return UriComponentsBuilder.fromUriString(base + "/api/v2/alarms")
                .queryParam("accesskey", key)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
    }

    private List<DiveraAlarmSummary> parseAlarmItems(JsonNode dataBlock) {
        List<DiveraAlarmSummary> alarms = new ArrayList<>();
        if (dataBlock == null || dataBlock.isNull()) {
            return alarms;
        }
        if (dataBlock.isArray()) {
            for (JsonNode item : dataBlock) {
                parseOneAlarm(item).ifPresent(alarms::add);
            }
            return alarms;
        }
        JsonNode items = dataBlock.path("items");
        if (items.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = items.fields();
            while (it.hasNext()) {
                parseOneAlarm(it.next().getValue()).ifPresent(alarms::add);
            }
            return alarms;
        }
        if (items.isArray()) {
            for (JsonNode item : items) {
                parseOneAlarm(item).ifPresent(alarms::add);
            }
            return alarms;
        }
        if (dataBlock.isObject() && dataBlock.has("id")) {
            parseOneAlarm(dataBlock).ifPresent(alarms::add);
        }
        return alarms;
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
        boolean closed = isClosed(item);
        return java.util.Optional.of(new DiveraAlarmSummary(
                id,
                item.path("title").asText(""),
                item.path("text").asText(""),
                item.path("address").asText(""),
                dateTs,
                tsCreate,
                closed));
    }

    private static boolean isClosed(JsonNode item) {
        JsonNode closedNode = item.path("closed");
        if (closedNode.isBoolean()) {
            return closedNode.asBoolean(false);
        }
        if (closedNode.isNumber()) {
            return closedNode.asInt(0) != 0;
        }
        return "1".equals(closedNode.asText("").trim()) || "true".equalsIgnoreCase(closedNode.asText(""));
    }

    private static String textOr(JsonNode n, String a, String b, String def) {
        String x = n.path(a).asText("");
        if (!x.isEmpty()) {
            return x;
        }
        x = n.path(b).asText("");
        return !x.isEmpty() ? x : def;
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
}
