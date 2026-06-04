package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Optional;

/** Parst DIVERA-Webhook-/API-JSON in ein einheitliches Alarm-Format. */
public final class DiveraAlarmJsonParser {

    private DiveraAlarmJsonParser() {}

    public record ParsedAlarm(
            long alarmId,
            String externalId,
            String title,
            String text,
            String address,
            long dateEpochSeconds,
            long tsCreateSeconds,
            boolean closed) {}

    public static Optional<ParsedAlarm> parseFirst(JsonNode root) {
        if (root == null || root.isNull()) {
            return Optional.empty();
        }
        JsonNode alarm = extractAlarmNode(root);
        if (alarm == null || alarm.isNull() || !alarm.isObject()) {
            return Optional.empty();
        }
        long id = alarm.path("id").asLong(0);
        if (id <= 0) {
            id = alarm.path("Id").asLong(0);
        }
        String foreign = textOrNull(alarm, "foreign_id", "ForeignId");
        if (id <= 0 && (foreign == null || foreign.isBlank())) {
            id = System.currentTimeMillis() % 1_000_000_000L;
            if (id <= 0) {
                id = 1;
            }
        }
        long dateTs = epochSeconds(alarm.path("date"));
        if (dateTs == 0) {
            dateTs = epochSeconds(alarm.path("ts_create"));
        }
        if (dateTs == 0) {
            dateTs = epochSeconds(alarm.path("TsCreate"));
        }
        if (dateTs == 0) {
            dateTs = epochSeconds(alarm.path("ts_publish"));
        }
        if (dateTs == 0) {
            dateTs = epochSeconds(alarm.path("TsPublish"));
        }
        long tsCreate = epochSeconds(alarm.path("ts_create"));
        if (tsCreate == 0) {
            tsCreate = epochSeconds(alarm.path("TsCreate"));
        }
        if (tsCreate == 0) {
            tsCreate = dateTs;
        }
        if (dateTs == 0) {
            dateTs = tsCreate;
        }
        String title = textOrNull(alarm, "title", "Title");
        if (title == null || title.isBlank()) {
            title = "Testalarm";
        }
        return Optional.of(new ParsedAlarm(
                id,
                foreign,
                title.trim(),
                textOrNull(alarm, "text", "Text"),
                textOrNull(alarm, "address", "Address"),
                dateTs,
                tsCreate,
                alarm.path("closed").asBoolean(false) || alarm.path("Closed").asBoolean(false)));
    }

    private static JsonNode extractAlarmNode(JsonNode root) {
        for (String dataKey : new String[] {"data", "Data"}) {
            if (!root.has(dataKey)) {
                continue;
            }
            JsonNode data = root.path(dataKey);
            for (String alarmKey : new String[] {"alarm", "Alarm"}) {
                if (!data.has(alarmKey)) {
                    continue;
                }
                JsonNode alarmObj = data.path(alarmKey);
                for (String itemsKey : new String[] {"items", "Items"}) {
                    if (!alarmObj.has(itemsKey)) {
                        continue;
                    }
                    JsonNode items = alarmObj.path(itemsKey);
                    JsonNode first = firstItem(items);
                    if (first != null) {
                        return first;
                    }
                }
            }
            if (data.has("id") || data.has("title") || data.has("Title")) {
                return data;
            }
        }
        for (String alarmKey : new String[] {"alarm", "Alarm"}) {
            if (root.has(alarmKey) && root.path(alarmKey).isObject()) {
                JsonNode alarmObj = root.path(alarmKey);
                JsonNode fromItems = firstItemInAlarmItems(alarmObj);
                if (fromItems != null) {
                    return fromItems;
                }
                if (alarmObj.has("id") || alarmObj.has("title") || alarmObj.has("Title")) {
                    return alarmObj;
                }
            }
        }
        if (root.has("id") || root.has("title") || root.has("Title")) {
            return root;
        }
        return root;
    }

    private static JsonNode firstItemInAlarmItems(JsonNode alarmObj) {
        for (String itemsKey : new String[] {"items", "Items"}) {
            if (!alarmObj.has(itemsKey)) {
                continue;
            }
            JsonNode items = alarmObj.path(itemsKey);
            if (items.isArray() && items.size() > 0) {
                return items.get(0);
            }
            if (items.isObject()) {
                Iterator<JsonNode> it = items.elements();
                if (it.hasNext()) {
                    return it.next();
                }
            }
        }
        return null;
    }

    private static JsonNode firstItem(JsonNode items) {
        if (items.isArray() && items.size() > 0) {
            return items.get(0);
        }
        if (items.isObject()) {
            Iterator<JsonNode> it = items.elements();
            if (it.hasNext()) {
                return it.next();
            }
        }
        return null;
    }

    private static long epochSeconds(JsonNode n) {
        if (n == null || n.isNull()) {
            return 0;
        }
        long v = n.asLong(0);
        if (v > 10_000_000_000L) {
            return v / 1000;
        }
        return v;
    }

    private static String textOrNull(JsonNode n, String... keys) {
        for (String key : keys) {
            String v = n.path(key).asText("");
            if (!v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
