package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiveraWebhookPayloadBuilder {

    private final ObjectMapper objectMapper;

    public String buildFromSummary(DiveraAlarmSummary alarm) {
        try {
            ObjectNode data = objectMapper.createObjectNode();
            data.put("id", alarm.id());
            data.put("title", alarm.title() != null ? alarm.title() : "");
            if (alarm.text() != null && !alarm.text().isBlank()) {
                data.put("text", alarm.text());
            }
            if (alarm.address() != null && !alarm.address().isBlank()) {
                data.put("address", alarm.address());
            }
            long ts = alarm.tsCreate() > 0 ? alarm.tsCreate() : alarm.dateEpochSeconds();
            if (ts > 0) {
                data.put("ts_create", ts);
            }
            data.put("closed", alarm.closed());
            data.put("foreign_id", "divera:" + alarm.id());
            ObjectNode root = objectMapper.createObjectNode();
            root.set("data", data);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Webhook-JSON konnte nicht erzeugt werden", e);
        }
    }

    public String buildFromParsed(DiveraAlarmJsonParser.ParsedAlarm alarm) {
        try {
            ObjectNode data = objectMapper.createObjectNode();
            data.put("id", alarm.alarmId());
            data.put("title", alarm.title());
            if (alarm.text() != null && !alarm.text().isBlank()) {
                data.put("text", alarm.text());
            }
            if (alarm.address() != null && !alarm.address().isBlank()) {
                data.put("address", alarm.address());
            }
            long ts = alarm.tsCreateSeconds() > 0 ? alarm.tsCreateSeconds() : alarm.dateEpochSeconds();
            if (ts > 0) {
                data.put("ts_create", ts);
            }
            data.put("closed", alarm.closed());
            if (alarm.externalId() != null && !alarm.externalId().isBlank()) {
                data.put("foreign_id", alarm.externalId());
            } else {
                data.put("foreign_id", "divera:" + alarm.alarmId());
            }
            ObjectNode root = objectMapper.createObjectNode();
            root.set("data", data);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Webhook-JSON konnte nicht erzeugt werden", e);
        }
    }
}
