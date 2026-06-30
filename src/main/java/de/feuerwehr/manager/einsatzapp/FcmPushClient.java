package de.feuerwehr.manager.einsatzapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Versand über FCM HTTP v1 API (Firebase-Dienstkonto). */
@Service
@RequiredArgsConstructor
@Slf4j
public class FcmPushClient {

    private final FcmAccessTokenProvider accessTokenProvider;
    private final FcmConfigService fcmConfigService;
    private final ObjectMapper objectMapper;

    public record FcmSendResult(int successCount, int failureCount, List<String> invalidTokens) {}

    public boolean isAvailable() {
        return fcmConfigService.isConfigured();
    }

    public FcmSendResult sendAlarmNotification(List<String> registrationTokens, String title, String body, long alarmId) {
        if (!isAvailable() || registrationTokens == null || registrationTokens.isEmpty()) {
            return new FcmSendResult(0, 0, List.of());
        }
        Optional<String> tokenOpt = accessTokenProvider.getAccessToken();
        Optional<String> projectOpt = accessTokenProvider.getProjectId();
        if (tokenOpt.isEmpty() || projectOpt.isEmpty()) {
            return new FcmSendResult(0, registrationTokens.size(), List.of());
        }
        String accessToken = tokenOpt.get();
        String projectId = projectOpt.get();
        Set<String> unique = new LinkedHashSet<>(registrationTokens);
        int success = 0;
        int failure = 0;
        List<String> invalid = new ArrayList<>();
        for (String deviceToken : unique) {
            if (deviceToken == null || deviceToken.isBlank()) {
                continue;
            }
            SendOutcome outcome = sendToDevice(accessToken, projectId, deviceToken.trim(), title, body, alarmId);
            if (outcome == SendOutcome.SUCCESS) {
                success++;
            } else {
                failure++;
                if (outcome == SendOutcome.INVALID_TOKEN) {
                    invalid.add(deviceToken.trim());
                }
            }
        }
        return new FcmSendResult(success, failure, invalid);
    }

    private enum SendOutcome {
        SUCCESS,
        FAILURE,
        INVALID_TOKEN
    }

    private SendOutcome sendToDevice(
            String accessToken, String projectId, String deviceToken, String title, String body, long alarmId) {
        try {
            String payload = buildMessagePayload(deviceToken, title, body, alarmId);
            RestClient client = restClient();
            String raw = client
                    .post()
                    .uri("https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json; UTF-8")
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            if (raw != null && raw.contains("\"name\"")) {
                return SendOutcome.SUCCESS;
            }
            return SendOutcome.FAILURE;
        } catch (RestClientResponseException e) {
            if (isInvalidDeviceToken(e)) {
                return SendOutcome.INVALID_TOKEN;
            }
            log.warn("[FCM] Versand an Gerät fehlgeschlagen ({}): {}", e.getStatusCode().value(), summarizeError(e));
            return SendOutcome.FAILURE;
        } catch (Exception e) {
            log.warn("[FCM] Versand fehlgeschlagen: {}", e.getMessage());
            return SendOutcome.FAILURE;
        }
    }

    private String buildMessagePayload(String deviceToken, String title, String body, long alarmId) throws Exception {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put("title", title != null && !title.isBlank() ? title.trim() : "Einsatz");
        notification.put("body", body != null && !body.isBlank() ? body.trim() : "Neuer DIVERA-Einsatz");

        ObjectNode data = objectMapper.createObjectNode();
        data.put("type", "divera_alarm");
        data.put("alarmId", String.valueOf(alarmId));

        ObjectNode android = objectMapper.createObjectNode();
        android.put("priority", "HIGH");

        ObjectNode message = objectMapper.createObjectNode();
        message.put("token", deviceToken);
        message.set("notification", notification);
        message.set("data", data);
        message.set("android", android);

        ObjectNode root = objectMapper.createObjectNode();
        root.set("message", message);
        return objectMapper.writeValueAsString(root);
    }

    private boolean isInvalidDeviceToken(RestClientResponseException e) {
        HttpStatusCode status = e.getStatusCode();
        if (!status.is4xxClientError()) {
            return false;
        }
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return status.value() == 404;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String statusText = root.path("error").path("status").asText("");
            if ("NOT_FOUND".equals(statusText) || "INVALID_ARGUMENT".equals(statusText)) {
                return true;
            }
            JsonNode details = root.path("error").path("details");
            if (details.isArray()) {
                for (JsonNode detail : details) {
                    String code = detail.path("errorCode").asText("");
                    if ("UNREGISTERED".equals(code) || "INVALID_ARGUMENT".equals(code)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            return body.contains("UNREGISTERED") || body.contains("NOT_FOUND");
        }
        return false;
    }

    private static String summarizeError(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return e.getMessage();
        }
        return body.length() > 240 ? body.substring(0, 237) + "…" : body;
    }

    private static RestClient restClient() {
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(5_000);
        rf.setReadTimeout(15_000);
        return RestClient.builder().requestFactory(rf).build();
    }
}
