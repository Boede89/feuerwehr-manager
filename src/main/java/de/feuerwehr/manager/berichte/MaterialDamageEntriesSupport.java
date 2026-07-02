package de.feuerwehr.manager.berichte;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class MaterialDamageEntriesSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

    private static final String EMPTY_JSON = "[]";

    private MaterialDamageEntriesSupport() {}

    public static MaterialDamageEntries parse(String json) {
        if (json == null || json.isBlank()) {
            return MaterialDamageEntries.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isArray()) {
                return MaterialDamageEntries.empty();
            }
            List<MaterialDamageEntry> entries = new ArrayList<>();
            for (JsonNode item : root) {
                entries.add(new MaterialDamageEntry(
                        textValue(item, "mangelAn"),
                        textValue(item, "bezeichnung"),
                        longValue(item, "vehicleId"),
                        textValue(item, "mangelBeschreibung"),
                        textValue(item, "ursache"),
                        textValue(item, "verbleib")));
            }
            return new MaterialDamageEntries(entries);
        } catch (Exception e) {
            log.warn("Sachschaden-JSON konnte nicht gelesen werden: {}", e.getMessage());
            return MaterialDamageEntries.empty();
        }
    }

    public static String serialize(MaterialDamageEntries entries) {
        if (entries == null) {
            return EMPTY_JSON;
        }
        try {
            return MAPPER.writeValueAsString(entries.normalized().entries());
        } catch (Exception e) {
            log.warn("Sachschaden-JSON konnte nicht geschrieben werden: {}", e.getMessage());
            return EMPTY_JSON;
        }
    }

    public static String emptyJson() {
        return EMPTY_JSON;
    }

    private static String textValue(JsonNode item, String field) {
        if (item == null || item.isNull()) {
            return null;
        }
        JsonNode value = item.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private static Long longValue(JsonNode item, String field) {
        if (item == null || item.isNull()) {
            return null;
        }
        JsonNode value = item.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        try {
            long parsed = value.asLong();
            return parsed > 0 ? parsed : null;
        } catch (Exception e) {
            return null;
        }
    }
}
