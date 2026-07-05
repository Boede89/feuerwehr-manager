package de.feuerwehr.manager.divera;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DiveraAlarmClosedSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void isClosed_falseWhenFieldMissing() throws Exception {
        assertThat(DiveraAlarmClosedSupport.isClosed(objectMapper.readTree("{\"id\":1}"))).isFalse();
    }

    @Test
    void isClosed_falseForZeroNumber() throws Exception {
        assertThat(DiveraAlarmClosedSupport.isClosed(objectMapper.readTree("{\"closed\":0}"))).isFalse();
    }

    @Test
    void isClosed_trueForOneNumber() throws Exception {
        assertThat(DiveraAlarmClosedSupport.isClosed(objectMapper.readTree("{\"closed\":1}"))).isTrue();
    }

    @Test
    void isClosed_trueForBooleanTrue() throws Exception {
        assertThat(DiveraAlarmClosedSupport.isClosed(objectMapper.readTree("{\"closed\":true}"))).isTrue();
    }

    @Test
    void isClosed_falseForStringZero() throws Exception {
        assertThat(DiveraAlarmClosedSupport.isClosed(objectMapper.readTree("{\"closed\":\"0\"}"))).isFalse();
    }

    @Test
    void isClosed_respectsCapitalizedKey() throws Exception {
        assertThat(DiveraAlarmClosedSupport.isClosed(objectMapper.readTree("{\"Closed\":true}"))).isTrue();
    }
}
