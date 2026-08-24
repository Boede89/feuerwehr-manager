package de.feuerwehr.manager.berichte;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IncidentCrewSupportTest {

    @Test
    void countsAsPresent_includesAllPersonnelSlots() {
        assertThat(IncidentCrewSupport.countsAsPresent(IncidentCrewSupport.BETEILIGT_VEHICLE_ID)).isTrue();
        assertThat(IncidentCrewSupport.countsAsPresent(IncidentCrewSupport.WACHE_VEHICLE_ID)).isTrue();
        assertThat(IncidentCrewSupport.countsAsPresent(IncidentCrewSupport.EINSATZSTELLE_VEHICLE_ID)).isTrue();
        assertThat(IncidentCrewSupport.countsAsPresent(12L)).isTrue();
        assertThat(IncidentCrewSupport.countsAsPresent(0L)).isFalse();
        assertThat(IncidentCrewSupport.countsAsPresent(-99L)).isFalse();
    }
}
