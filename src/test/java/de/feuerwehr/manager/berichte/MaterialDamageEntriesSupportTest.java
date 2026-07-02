package de.feuerwehr.manager.berichte;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaterialDamageEntriesSupportTest {

    @Test
    void parseAndSerializeMaterialDamageEntries() {
        String json =
                "[{\"mangelAn\":\"GERAET\",\"bezeichnung\":\"Atemschutzgerät 42\","
                        + "\"vehicleId\":7,\"mangelBeschreibung\":\"Riss im Schlauch\","
                        + "\"ursache\":\"Hitze\",\"verbleib\":\"Werkstatt\"}]";

        MaterialDamageEntries entries = MaterialDamageEntriesSupport.parse(json);

        assertThat(entries.entries()).hasSize(1);
        MaterialDamageEntry entry = entries.entries().getFirst();
        assertThat(entry.mangelAn()).isEqualTo("GERAET");
        assertThat(entry.bezeichnung()).isEqualTo("Atemschutzgerät 42");
        assertThat(entry.vehicleId()).isEqualTo(7L);
        assertThat(entry.mangelBeschreibung()).isEqualTo("Riss im Schlauch");
        assertThat(entry.hasContent()).isTrue();

        String serialized = MaterialDamageEntriesSupport.serialize(entries.normalized());
        assertThat(serialized).contains("Atemschutzgerät 42");
        assertThat(serialized).contains("GERAET");
    }

    @Test
    void emptyEntriesAreFilteredOnNormalize() {
        MaterialDamageEntries entries = MaterialDamageEntriesSupport.parse(
                "[{\"mangelAn\":\"GEBAEUDE\",\"bezeichnung\":\"\",\"vehicleId\":null,"
                        + "\"mangelBeschreibung\":\"\",\"ursache\":\"\",\"verbleib\":\"\"}]");

        assertThat(entries.normalized().entries()).isEmpty();
        assertThat(MaterialDamageEntriesSupport.emptyJson()).isEqualTo("[]");
    }
}
