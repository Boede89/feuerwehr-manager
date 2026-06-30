package de.feuerwehr.manager.einsatzapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Versand über FCM Legacy HTTP API (Server Key). */
@Service
@RequiredArgsConstructor
@Slf4j
public class FcmPushClient {

    private static final String FCM_URL = "https://fcm.googleapis.com/fcm/send";

    private final FcmProperties fcmProperties;
    private final ObjectMapper objectMapper;

    public record FcmSendResult(int successCount, int failureCount, List<String> invalidTokens) {}

    public boolean isAvailable() {
        return fcmProperties.isConfigured();
    }

    public FcmSendResult sendAlarmNotification(List<String> registrationTokens, String title, String body, long alarmId) {
        if (!isAvailable() || registrationTokens == null || registrationTokens.isEmpty()) {
            return new FcmSendResult(0, 0, List.of());
        }
        Set<String> unique = new LinkedHashSet<>(registrationTokens);
        List<String> tokens = new ArrayList<>(unique);
        int success = 0;
        int failure = 0;
        List<String> invalid = new ArrayList<>();
        for (int offset = 0; offset < tokens.size(); offset += 500) {
            List<String> batch = tokens.subList(offset, Math.min(offset + 500, tokens.size()));
            FcmSendResult batchResult = sendBatch(batch, title, body, alarmId);
            success += batchResult.successCount();
            failure += batchResult.failureCount();
            invalid.addAll(batchResult.invalidTokens());
        }
        return new FcmSendResult(success, failure, invalid);
    }

    private FcmSendResult sendBatch(List<String> tokens, String title, String body, long alarmId) {
        try {
            ObjectNode notification = objectMapper.createObjectNode();
            notification.put("title", title != null && !title.isBlank() ? title.trim() : "Einsatz");
            notification.put("body", body != null && !body.isBlank() ? body.trim() : "Neuer DIVERA-Einsatz");
            notification.put("sound", "default");

            ObjectNode data = objectMapper.createObjectNode();
            data.put("type", "divera_alarm");
            data.put("alarmId", alarmId);

            ObjectNode root = objectMapper.createObjectNode();
            root.put("priority", "high");
            root.set("notification", notification);
            root.set("data", data);
            if (tokens.size() == 1) {
                root.put("to", tokens.get(0));
            } else {
                root.set("registration_ids", objectMapper.valueToTree(tokens));
            }

            RestClient client = restClient();
            String raw = client
                    .post()
                    .uri(FCM_URL)
                    .header("Authorization", "key=" + fcmProperties.serverKey().trim())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body(root.toString())
                    .retrieve()
                    .body(String.class);

            return parseResponse(raw, tokens);
        } catch (Exception e) {
            log.warn("[FCM] Versand fehlgeschlagen: {}", e.getMessage());
            return new FcmSendResult(0, tokens.size(), List.of());
        }
    }

    private FcmSendResult parseResponse(String raw, List<String> tokens) {
        if (raw == null || raw.isBlank()) {
            return new FcmSendResult(0, tokens.size(), List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            int success = root.path("success").asInt(0);
            int failure = root.path("failure").asInt(0);
            List<String> invalid = new ArrayList<>();
            JsonNode results = root.path("results");
            if (results.isArray()) {
                for (int i = 0; i < results.size() && i < tokens.size(); i++) {
                    JsonNode item = results.get(i);
                    String error = item.path("error").asText("");
                    if ("NotRegistered".equals(error) || "InvalidRegistration".equals(error)) {
                        invalid.add(tokens.get(i));
                    }
                }
            }
            return new FcmSendResult(success, failure, invalid);
        } catch (Exception e) {
            log.warn("[FCM] Antwort nicht lesbar: {}", e.getMessage());
            return new FcmSendResult(0, tokens.size(), List.of());
        }
    }

    private static RestClient restClient() {
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(5_000);
        rf.setReadTimeout(15_000);
        return RestClient.builder().requestFactory(rf).build();
    }
}
