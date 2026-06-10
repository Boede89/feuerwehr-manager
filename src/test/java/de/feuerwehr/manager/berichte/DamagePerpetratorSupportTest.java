package de.feuerwehr.manager.berichte;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DamagePerpetratorSupportTest {

    @Test
    void parseAndSerializePerpetratorDetails() {
        String json =
                "{\"name\":\"Erika Muster\",\"address\":\"Beispielweg 2, 12345 Musterstadt\","
                        + "\"birthdate\":\"1980-05-12\",\"licensePlate\":\"AB-C 1234\"}";

        DamagePerpetratorDetails details = DamagePerpetratorSupport.parse(json);

        assertThat(details.name()).isEqualTo("Erika Muster");
        assertThat(details.address()).isEqualTo("Beispielweg 2, 12345 Musterstadt");
        assertThat(details.birthdate()).isEqualTo("1980-05-12");
        assertThat(details.licensePlate()).isEqualTo("AB-C 1234");
        assertThat(details.hasContent()).isTrue();

        String serialized = DamagePerpetratorSupport.serialize(details.normalized());
        assertThat(serialized).contains("Erika Muster");
        assertThat(serialized).contains("AB-C 1234");

        DamagePerpetratorDetails emptyBirthdate = DamagePerpetratorSupport.parse(
                "{\"name\":\"Max\",\"address\":\"\",\"birthdate\":\"\",\"licensePlate\":null}");
        assertThat(emptyBirthdate.normalized().name()).isEqualTo("Max");
        assertThat(emptyBirthdate.normalized().birthdate()).isNull();
    }
}
