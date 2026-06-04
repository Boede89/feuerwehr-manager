package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Serialisiert DIVERA-Alarm-JSON mit allen gelieferten Feldern. */
@Component
@RequiredArgsConstructor
public class DiveraAlarmRawJson {

    private final ObjectMapper objectMapper;

    /** Komplettes Alarm-Objekt aus der API (alle Felder von DIVERA). */
    public String serializeAlarmItem(JsonNode item) {
        if (item == null || item.isNull() || !item.isObject()) {
            return "{}";
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(item);
        } catch (Exception e) {
            throw new IllegalStateException("Alarm-JSON konnte nicht serialisiert werden", e);
        }
    }

    /** Vollständiger Webhook-Request-Body wie von DIVERA gesendet. */
    public String serializeWebhookBody(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return rawBody;
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody.trim());
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return rawBody.trim();
        }
    }

    /**
     * API-Item als Webhook-Payload: {@code data} enthält das unveränderte DIVERA-Alarm-Objekt inkl. aller Felder.
     */
    public String wrapApiItemAsWebhookPayload(JsonNode item) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.set("data", item.deepCopy());
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return serializeAlarmItem(item);
        }
    }
}
