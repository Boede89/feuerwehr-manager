package de.feuerwehr.manager.berichte;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class DamagePerpetratorSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

    private static final String EMPTY_JSON =
            "{\"name\":null,\"address\":null,\"birthdate\":null,\"licensePlate\":null}";

    private DamagePerpetratorSupport() {}

    public static DamagePerpetratorDetails parse(String json) {
        if (json == null || json.isBlank()) {
            return DamagePerpetratorDetails.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            return new DamagePerpetratorDetails(
                    textValue(root, "name"),
                    textValue(root, "address"),
                    textValue(root, "birthdate"),
                    textValue(root, "licensePlate"));
        } catch (Exception e) {
            log.warn("Verursacher-JSON konnte nicht gelesen werden: {}", e.getMessage());
            return DamagePerpetratorDetails.empty();
        }
    }

    public static String serialize(DamagePerpetratorDetails details) {
        if (details == null) {
            return EMPTY_JSON;
        }
        try {
            return MAPPER.writeValueAsString(details);
        } catch (Exception e) {
            log.warn("Verursacher-JSON konnte nicht geschrieben werden: {}", e.getMessage());
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
}
