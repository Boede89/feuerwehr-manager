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

    @Test
    void parsePersonnelResponses_readsNestedUcrAnswered() throws Exception {
        String json =
                """
                {
                  "id": 99,
                  "ucr_answered": {
                    "44986": {
                      "230073": { "ts": 1780784431, "note": "" },
                      "230074": { "ts": 1780784432, "note": "unterwegs" }
                    }
                  }
                }
                """;

        var hits = DiveraAlarmDetailsMapper.parsePersonnelResponses(objectMapper.readTree(json));

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).ucrId()).isIn("230073", "230074");
        assertThat(hits.get(0).statusId()).isEqualTo("44986");
    }

    @Test
    void parsePersonnelResponses_readsFlatUcrAnsweredArray() throws Exception {
        String json =
                """
                {
                  "id": 100,
                  "ucr_answered": [230073, 230074]
                }
                """;

        var hits = DiveraAlarmDetailsMapper.parsePersonnelResponses(objectMapper.readTree(json));

        assertThat(hits).hasSize(2);
        assertThat(hits).extracting(DiveraAlarmDetailsMapper.DiveraPersonnelHit::ucrId)
                .containsExactlyInAnyOrder("230073", "230074");
        assertThat(hits).allMatch(hit -> hit.statusId() == null);
    }

    @Test
    void fromWebhookJson_includesUcrAnsweredInDetails() throws Exception {
        String json =
                """
                {
                  "data": {
                    "id": 101,
                    "title": "B3",
                    "ucr_answered": [230073]
                  }
                }
                """;

        var details = DiveraAlarmDetailsMapper.fromWebhookJson(objectMapper.readTree(json)).orElseThrow();

        assertThat(DiveraAlarmDetailsMapper.hasPersonnelResponses(details)).isTrue();
        assertThat(details.answeredUcrIds()).containsExactly(230073L);
    }
}
