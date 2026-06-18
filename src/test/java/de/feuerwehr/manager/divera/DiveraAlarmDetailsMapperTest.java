package de.feuerwehr.manager.divera;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DiveraAlarmDetailsMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fromWebhookJson_splitsStreetFieldIntoStreetAndHouseNumber() throws Exception {
        String json =
                """
                {
                  "data": {
                    "alarm": {
                      "items": [{
                        "id": 42,
                        "title": "B1 Wohnungsbrand",
                        "street": "Geneschen 109 B",
                        "zip": "41366",
                        "city": "Schwalmtal"
                      }]
                    }
                  }
                }
                """;

        var details = DiveraAlarmDetailsMapper.fromWebhookJson(objectMapper.readTree(json)).orElseThrow();

        assertThat(details.street()).isEqualTo("Geneschen");
        assertThat(details.houseNumber()).isEqualTo("109 B");
        assertThat(details.postalCode()).isEqualTo("41366");
        assertThat(details.city()).isEqualTo("Schwalmtal");
    }

    @Test
    void fromWebhookJson_splitsCombinedAddressLine() throws Exception {
        String json =
                """
                {
                  "data": {
                    "alarm": {
                      "items": [{
                        "id": 43,
                        "title": "THL",
                        "address": "Geneschen 109 B, 41366 Schwalmtal Amern"
                      }]
                    }
                  }
                }
                """;

        var details = DiveraAlarmDetailsMapper.fromWebhookJson(objectMapper.readTree(json)).orElseThrow();

        assertThat(details.street()).isEqualTo("Geneschen");
        assertThat(details.houseNumber()).isEqualTo("109 B");
        assertThat(details.postalCode()).isEqualTo("41366");
    }
}
