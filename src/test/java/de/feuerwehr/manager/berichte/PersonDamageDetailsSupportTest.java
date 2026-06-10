package de.feuerwehr.manager.berichte;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PersonDamageDetailsSupportTest {

    @Test
    void parseAcceptsEmptyBirthdateAndPersistsNames() {
        String json =
                "{\"rescued\":[{\"name\":\"Max Mustermann\",\"address\":\"Musterstraße 1\",\"birthdate\":\"\"}],"
                        + "\"injured\":[],\"recovered\":[],\"dead\":[]}";

        PersonDamageDetails details = PersonDamageDetailsSupport.parse(json);

        assertThat(details.rescued()).hasSize(1);
        assertThat(details.rescued().get(0).name()).isEqualTo("Max Mustermann");
        assertThat(details.rescued().get(0).address()).isEqualTo("Musterstraße 1");
        assertThat(details.rescued().get(0).birthdate()).isNull();

        String serialized = PersonDamageDetailsSupport.serialize(details.normalized(1, 0, 0, 0));
        assertThat(serialized).contains("Max Mustermann");
        assertThat(serialized).contains("Musterstraße 1");
    }
}
