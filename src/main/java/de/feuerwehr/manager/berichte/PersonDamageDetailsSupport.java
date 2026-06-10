package de.feuerwehr.manager.berichte;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class PersonDamageDetailsSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

    private static final String EMPTY_JSON = "{\"rescued\":[],\"injured\":[],\"recovered\":[],\"dead\":[]}";

    private PersonDamageDetailsSupport() {}

    public static PersonDamageDetails parse(String json) {
        if (json == null || json.isBlank()) {
            return PersonDamageDetails.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            return new PersonDamageDetails(
                    parseEntries(root.get("rescued")),
                    parseEntries(root.get("injured")),
                    parseEntries(root.get("recovered")),
                    parseEntries(root.get("dead")));
        } catch (Exception e) {
            log.warn("Personenschaden-JSON konnte nicht gelesen werden: {}", e.getMessage());
            return PersonDamageDetails.empty();
        }
    }

    public static String serialize(PersonDamageDetails details) {
        if (details == null) {
            return EMPTY_JSON;
        }
        try {
            return MAPPER.writeValueAsString(details);
        } catch (Exception e) {
            log.warn("Personenschaden-JSON konnte nicht geschrieben werden: {}", e.getMessage());
            return EMPTY_JSON;
        }
    }

    public static String emptyJson() {
        return EMPTY_JSON;
    }

    private static List<PersonDamageEntry> parseEntries(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<PersonDamageEntry> entries = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            entries.add(new PersonDamageEntry(
                    textValue(item, "name"),
                    textValue(item, "address"),
                    textValue(item, "birthdate")));
        }
        return entries;
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
}
