package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.databind.JsonNode;

/** Einheitliche Erkennung, ob ein DIVERA-Alarm geschlossen ist (Webhook + API). */
public final class DiveraAlarmClosedSupport {

    private DiveraAlarmClosedSupport() {}

    public static boolean isClosed(JsonNode alarm) {
        if (alarm == null || alarm.isNull() || !alarm.isObject()) {
            return false;
        }
        for (String key : new String[] {"closed", "Closed"}) {
            if (!alarm.has(key)) {
                continue;
            }
            return parseClosedValue(alarm.path(key));
        }
        return false;
    }

    private static boolean parseClosedValue(JsonNode closedNode) {
        if (closedNode == null || closedNode.isNull()) {
            return false;
        }
        if (closedNode.isBoolean()) {
            return closedNode.asBoolean(false);
        }
        if (closedNode.isNumber()) {
            return closedNode.asInt(0) != 0;
        }
        String text = closedNode.asText("").trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }
}
