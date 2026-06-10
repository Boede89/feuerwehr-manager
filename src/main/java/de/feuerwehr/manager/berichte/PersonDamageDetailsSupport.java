package de.feuerwehr.manager.berichte;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public final class PersonDamageDetailsSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EMPTY_JSON = "{\"rescued\":[],\"injured\":[],\"recovered\":[],\"dead\":[]}";

    private PersonDamageDetailsSupport() {}

    public static PersonDamageDetails parse(String json) {
        if (json == null || json.isBlank()) {
            return PersonDamageDetails.empty();
        }
        try {
            PersonDamageDetails parsed = MAPPER.readValue(json, PersonDamageDetails.class);
            if (parsed == null) {
                return PersonDamageDetails.empty();
            }
            return new PersonDamageDetails(
                    safeList(parsed.rescued()),
                    safeList(parsed.injured()),
                    safeList(parsed.recovered()),
                    safeList(parsed.dead()));
        } catch (Exception e) {
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
            return EMPTY_JSON;
        }
    }

    public static String emptyJson() {
        return EMPTY_JSON;
    }

    private static List<PersonDamageEntry> safeList(List<PersonDamageEntry> list) {
        return list != null ? list : List.of();
    }
}
