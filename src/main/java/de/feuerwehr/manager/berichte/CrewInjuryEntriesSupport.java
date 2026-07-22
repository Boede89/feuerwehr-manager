package de.feuerwehr.manager.berichte;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class CrewInjuryEntriesSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

    private static final String EMPTY_JSON = "[]";

    private CrewInjuryEntriesSupport() {}

    public static List<CrewInjuryEntry> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isArray()) {
                return List.of();
            }
            List<CrewInjuryEntry> entries = new ArrayList<>();
            for (JsonNode item : root) {
                Long personId = longValue(item, "personId");
                String personName = textValue(item, "personName");
                String time = textValue(item, "time");
                String description = textValue(item, "description");
                if ((personName == null || personName.isBlank())
                        && (description == null || description.isBlank())
                        && personId == null) {
                    continue;
                }
                entries.add(new CrewInjuryEntry(personId, personName, time, description));
            }
            return entries;
        } catch (Exception e) {
            log.warn("Personenschaden-JSON konnte nicht gelesen werden: {}", e.getMessage());
            return List.of();
        }
    }

    public static String serialize(List<CrewInjuryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return EMPTY_JSON;
        }
        try {
            return MAPPER.writeValueAsString(entries);
        } catch (Exception e) {
            log.warn("Personenschaden-JSON konnte nicht geschrieben werden: {}", e.getMessage());
            return EMPTY_JSON;
        }
    }

    public static String emptyJson() {
        return EMPTY_JSON;
    }

    public static boolean hasEntries(String json) {
        return !parse(json).isEmpty();
    }

    private static String textValue(JsonNode item, String field) {
        JsonNode node = item.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private static Long longValue(JsonNode item, String field) {
        JsonNode node = item.get(field);
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        long value = node.asLong();
        return value > 0 ? value : null;
    }
}
