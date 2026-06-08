package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mappt DIVERA-Alarm-JSON auf Einsatzbericht-Felder. */
public final class DiveraAlarmDetailsMapper {

    private static final Pattern PLZ_PATTERN = Pattern.compile("\\b(\\d{5})\\b");
    private static final Pattern STREET_WITH_NUMBER =
            Pattern.compile("^(.+?)\\s+(\\d+[a-zA-Z]?)(?:\\s*[,/].*)?$");

    private DiveraAlarmDetailsMapper() {}

    public record DiveraPersonnelHit(String ucrId, String statusId, String displayName) {}

    public record DiveraAlarmDetails(
            long alarmId,
            String externalId,
            String title,
            String text,
            String address,
            long dateEpochSeconds,
            long tsCreateSeconds,
            long tsCloseSeconds,
            long tsDepartureSeconds,
            long tsArrivalSeconds,
            boolean closed,
            String postalCode,
            String street,
            String houseNumber,
            String city,
            String district,
            String objekt,
            String eigentuemer,
            String reporterName,
            String reporterPhone,
            String fireObject,
            String situation,
            String measures,
            boolean falseAlarm,
            boolean maliciousAlarm,
            List<Long> answeredUcrIds,
            List<DiveraPersonnelHit> personnelHits) {}

    public static Optional<DiveraAlarmDetails> fromSummary(DiveraAlarmSummary summary, JsonNode root) {
        if (summary == null || summary.id() <= 0) {
            return Optional.empty();
        }
        if (root != null && !root.isNull()) {
            Optional<DiveraAlarmJsonParser.ParsedAlarm> parsed = DiveraAlarmJsonParser.parseFirst(root);
            if (parsed.isPresent()) {
                return Optional.of(fromAlarmNode(extractAlarmNode(root), parsed.get()));
            }
        }
        AddressParts address = parseAddress(summary.address());
        return Optional.of(new DiveraAlarmDetails(
                summary.id(),
                null,
                blankToNull(summary.title()),
                blankToNull(summary.text()),
                blankToNull(summary.address()),
                summary.dateEpochSeconds(),
                summary.tsCreate(),
                0,
                0,
                0,
                summary.closed(),
                address.postalCode(),
                address.street(),
                address.houseNumber(),
                address.city(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                List.of(),
                List.of()));
    }

    public static Optional<DiveraAlarmDetails> fromWebhookJson(JsonNode root) {
        Optional<DiveraAlarmJsonParser.ParsedAlarm> parsed = DiveraAlarmJsonParser.parseFirst(root);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(fromAlarmNode(extractAlarmNode(root), parsed.get()));
    }

    private static DiveraAlarmDetails fromAlarmNode(JsonNode alarm, DiveraAlarmJsonParser.ParsedAlarm parsed) {
        AddressParts structured = parseAddress(parsed.address());
        AddressParts merged = mergeAddress(structured, alarm);
        List<DiveraPersonnelHit> personnel = collectPersonnel(alarm);
        List<Long> answeredUcrIds = parseUcrIdArray(alarm, "ucr_answered");
        return new DiveraAlarmDetails(
                parsed.alarmId(),
                blankToNull(parsed.externalId()),
                blankToNull(parsed.title()),
                blankToNull(parsed.text()),
                blankToNull(parsed.address()),
                parsed.dateEpochSeconds(),
                parsed.tsCreateSeconds(),
                epochSeconds(alarm, "ts_close", "ts_end", "TsClose", "closed_at", "closedAt"),
                epochSeconds(alarm, "ts_departure", "ts_dispatch", "TsDeparture"),
                epochSeconds(alarm, "ts_arrival", "ts_on_scene", "TsArrival"),
                parsed.closed(),
                merged.postalCode(),
                merged.street(),
                merged.houseNumber(),
                merged.city(),
                textOrNull(alarm, "district", "kreis", "District", "county"),
                textOrNull(alarm, "object", "objekt", "object_name", "Object", "Objekt"),
                textOrNull(alarm, "owner", "eigentuemer", "eigentümer", "Owner", "Eigentuemer"),
                textOrNull(alarm, "caller", "melder", "reporter", "reporter_name", "caller_name", "Melder"),
                textOrNull(alarm, "caller_phone", "reporter_phone", "melder_phone", "phone_caller"),
                textOrNull(alarm, "fire_object", "brandobjekt", "FireObject"),
                textOrNull(alarm, "info", "additional_info", "situation", "lage", "Info"),
                textOrNull(alarm, "measures", "massnahme", "massnahmen", "action", "Actions"),
                boolOrFalse(alarm, "false_alarm", "fehlalarm", "FalseAlarm"),
                boolOrFalse(alarm, "malicious_alarm", "boeswillig", "boewillig", "MaliciousAlarm"),
                answeredUcrIds,
                personnel);
    }

    private static List<Long> parseUcrIdArray(JsonNode alarm, String... keys) {
        List<Long> result = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (String key : keys) {
            JsonNode node = alarm.path(key);
            if (!node.isArray()) {
                continue;
            }
            for (JsonNode item : node) {
                long id = extractUcrId(item);
                if (id > 0 && seen.add(id)) {
                    result.add(id);
                }
            }
        }
        return result;
    }

    private static long extractUcrId(JsonNode item) {
        if (item == null || item.isNull()) {
            return 0;
        }
        if (item.isNumber()) {
            return item.asLong(0);
        }
        if (item.isTextual()) {
            try {
                return Long.parseLong(item.asText("").trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        if (item.isObject()) {
            for (String key :
                    new String[] {"user_cluster_relation_id", "ucr_id", "ucrId", "UCRId", "id", "user_id", "userId"}) {
                long id = item.path(key).asLong(0);
                if (id > 0) {
                    return id;
                }
            }
        }
        return 0;
    }

    private static AddressParts mergeAddress(AddressParts fromAddress, JsonNode alarm) {
        String postalCode = firstNonBlank(
                textOrNull(alarm, "zip", "plz", "postal_code", "postalCode", "Zip"),
                fromAddress.postalCode());
        String street = firstNonBlank(
                textOrNull(alarm, "street", "strasse", "Strasse", "Street"),
                fromAddress.street());
        String houseNumber = firstNonBlank(
                textOrNull(alarm, "house_number", "houseNumber", "houseno", "number", "hausnummer"),
                fromAddress.houseNumber());
        String city = firstNonBlank(
                textOrNull(alarm, "city", "ort", "locality", "gemeinde", "City", "Ort"),
                fromAddress.city());
        return new AddressParts(postalCode, street, houseNumber, city);
    }

    private static AddressParts parseAddress(String address) {
        if (address == null || address.isBlank()) {
            return new AddressParts(null, null, null, null);
        }
        String trimmed = address.trim();
        String postalCode = null;
        Matcher plzMatcher = PLZ_PATTERN.matcher(trimmed);
        if (plzMatcher.find()) {
            postalCode = plzMatcher.group(1);
        }

        String city = null;
        String street = null;
        String houseNumber = null;

        String[] parts = trimmed.split(",");
        if (parts.length >= 2) {
            String left = parts[0].trim();
            String right = parts[parts.length - 1].trim();
            if (postalCode != null && right.startsWith(postalCode)) {
                city = right.substring(postalCode.length()).trim();
                street = parseStreetLine(left);
            } else if (postalCode != null && left.matches(".*\\b" + postalCode + "\\b.*")) {
                city = left.replace(postalCode, "").trim().replaceAll("^[,\\s]+|[,\\s]+$", "");
                street = parseStreetLine(right);
            } else {
                street = parseStreetLine(left);
                city = right.replace(postalCode != null ? postalCode : "", "").trim();
            }
        } else {
            street = parseStreetLine(trimmed);
        }

        Matcher streetMatcher = street != null ? STREET_WITH_NUMBER.matcher(street) : null;
        if (streetMatcher != null && streetMatcher.matches()) {
            street = streetMatcher.group(1).trim();
            houseNumber = streetMatcher.group(2).trim();
        }

        if (city != null && city.isBlank()) {
            city = null;
        }
        return new AddressParts(postalCode, street, houseNumber, city);
    }

    private static String parseStreetLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        return line.trim();
    }

    private static List<DiveraPersonnelHit> collectPersonnel(JsonNode alarm) {
        List<DiveraPersonnelHit> hits = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectPersonnelRecursive(alarm, hits, seen, 0);
        return hits;
    }

    private static void collectPersonnelRecursive(
            JsonNode node, List<DiveraPersonnelHit> hits, Set<String> seen, int depth) {
        if (node == null || node.isNull() || depth > 14) {
            return;
        }
        if (node.isObject()) {
            String statusId = textOrNull(node, "status_id", "statusId", "status", "StatusId");
            String ucrId = textOrNull(node, "ucr_id", "ucrId", "UCRId", "user_id", "userId", "ucr");
            String name = textOrNull(node, "name", "display_name", "fullname", "full_name", "user_name");
            if (statusId != null && (ucrId != null || name != null)) {
                String key = (ucrId != null ? ucrId : "") + "|" + statusId + "|" + (name != null ? name : "");
                if (seen.add(key)) {
                    hits.add(new DiveraPersonnelHit(ucrId, statusId, name));
                }
            }
            node.fields().forEachRemaining(entry -> collectPersonnelRecursive(entry.getValue(), hits, seen, depth + 1));
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectPersonnelRecursive(child, hits, seen, depth + 1);
            }
        }
    }

    static JsonNode extractAlarmNode(JsonNode root) {
        if (root == null || root.isNull()) {
            return root;
        }
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
                JsonNode fromItems = firstItemInAlarmItems(alarmObj);
                if (fromItems != null) {
                    return fromItems;
                }
            }
            if (data.has("id") || data.has("title") || data.has("Title")) {
                return data;
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
            if (items.isArray() && !items.isEmpty()) {
                return items.get(0);
            }
            if (items.isObject()) {
                var it = items.elements();
                if (it.hasNext()) {
                    return it.next();
                }
            }
        }
        return null;
    }

    private static long epochSeconds(JsonNode node, String... keys) {
        for (String key : keys) {
            long v = epochSeconds(node.path(key));
            if (v > 0) {
                return v;
            }
        }
        return 0;
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

    private static boolean boolOrFalse(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (value.isBoolean()) {
                return value.asBoolean(false);
            }
            if (value.isNumber()) {
                return value.asInt(0) != 0;
            }
            String text = value.asText("").trim();
            if ("1".equals(text) || "true".equalsIgnoreCase(text)) {
                return true;
            }
        }
        return false;
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record AddressParts(String postalCode, String street, String houseNumber, String city) {}
}
