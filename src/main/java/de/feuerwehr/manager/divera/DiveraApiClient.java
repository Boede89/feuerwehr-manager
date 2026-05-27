package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Divera 24/7 REST – entspricht dem bisherigen Abruf {@code GET /api/v2/alarms?accesskey=…}
 * (vgl. {@code fetch_divera_alarms} in der PHP-Anwendung).
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

    public DiveraAlarmsResponse fetchOpenAlarms(String apiBaseUrl, String accessKey) {
        String key = accessKey == null ? "" : accessKey.trim().replaceAll("[\\r\\n\\t\\v]+", "");
        if (key.isEmpty()) {
            return DiveraAlarmsResponse.fail("Divera Access Key fehlt (in den Einheitseinstellungen hinterlegen)");
        }
        String base = trimTrailingSlash(apiBaseUrl);
        if (base.isEmpty()) {
            base = "https://app.divera247.com";
        }
        URI uri = UriComponentsBuilder.fromUriString(base + "/api/v2/alarms")
                .queryParam("accesskey", key)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();

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
            JsonNode dataBlock = root.path("data");
            JsonNode items = dataBlock.path("items");
            if (!items.isArray()) {
                items = dataBlock;
            }
            if (!items.isArray()) {
                return DiveraAlarmsResponse.ok(List.of());
            }
            List<DiveraAlarmSummary> alarms = new ArrayList<>();
            for (JsonNode item : items) {
                if (!item.isObject()) {
                    continue;
                }
                long id = item.path("id").asLong(0);
                if (id <= 0) {
                    continue;
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
                boolean closed = item.path("closed").asBoolean(false);
                alarms.add(new DiveraAlarmSummary(
                        id,
                        item.path("title").asText(""),
                        item.path("text").asText(""),
                        item.path("address").asText(""),
                        dateTs,
                        tsCreate,
                        closed));
            }
            alarms.sort(Comparator.comparingLong(DiveraAlarmSummary::dateEpochSeconds).reversed());
            return DiveraAlarmsResponse.ok(alarms);
        } catch (RestClientResponseException e) {
            return DiveraAlarmsResponse.fail("Divera-HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            return DiveraAlarmsResponse.fail("Divera-API: " + e.getMessage());
        }
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
