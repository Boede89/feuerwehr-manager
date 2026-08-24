package de.feuerwehr.manager.berichte;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class KraefteFahrzeugeStateTest {

    @Test
    void occupiedCrewSlots_includesWacheEinsatzstelleAndUninvolvedVehiclesWithCrew() {
        var anwesend = person(1, "Anwesend");
        var hlf = person(2, "Auf HLF");
        var mtf = person(3, "Auf MTF ohne Einsatz");
        var stelle = person(4, "Einsatzstelle");
        var wache = person(5, "Wache");

        var state = new KraefteFahrzeugeState(
                List.of(),
                List.of(),
                List.of(),
                slot(IncidentCrewSupport.BETEILIGT_VEHICLE_ID, "Anwesend", List.of(anwesend), false),
                slot(IncidentCrewSupport.EINSATZSTELLE_VEHICLE_ID, "Einsatzstelle", List.of(stelle), false),
                slot(IncidentCrewSupport.WACHE_VEHICLE_ID, "Wache", List.of(wache), false),
                List.of(
                        slot(10L, "HLF 20", List.of(hlf), true),
                        slot(11L, "MTF", List.of(mtf), false),
                        slot(12L, "DLK leer", List.of(), true)));

        assertThat(state.occupiedCrewSlots())
                .extracting(KraefteFahrzeugeState.KraefteVehicleView::name)
                .containsExactly("Anwesend", "HLF 20", "MTF", "Einsatzstelle", "Wache");
        assertThat(state.occupiedCrewSlots().stream().flatMap(slot -> slot.crewPersons().stream()))
                .extracting(KraefteFahrzeugeState.KraeftePersonView::displayName)
                .containsExactly("Anwesend", "Auf HLF", "Auf MTF ohne Einsatz", "Einsatzstelle", "Wache");
    }

    @Test
    void paCsaMark_usesCsaInsteadOfSingleLetter() {
        assertThat(person(1, "Anwesend").paCsaMark()).isEmpty();
        assertThat(new KraefteFahrzeugeState.KraeftePersonView(
                        2, "PA", "M", 0, null, true, false, "manual", null, null, false)
                .paCsaMark())
                .isEqualTo("X");
        assertThat(new KraefteFahrzeugeState.KraeftePersonView(
                        3, "CSA", "M", 0, null, false, true, "manual", null, null, false)
                .paCsaMark())
                .isEqualTo("CSA");
        assertThat(new KraefteFahrzeugeState.KraeftePersonView(
                        4, "Beides", "M", 0, null, true, true, "manual", null, null, false)
                .paCsaMark())
                .isEqualTo("X/CSA");
    }

    private static KraefteFahrzeugeState.KraeftePersonView person(long id, String name) {
        return new KraefteFahrzeugeState.KraeftePersonView(
                id, name, "M", 0, null, false, false, "manual", null, null, false);
    }

    private static KraefteFahrzeugeState.KraefteVehicleView slot(
            long id, String name, List<KraefteFahrzeugeState.KraeftePersonView> crew, boolean involved) {
        return new KraefteFahrzeugeState.KraefteVehicleView(
                id,
                name,
                null,
                null,
                crew.stream().map(KraefteFahrzeugeState.KraeftePersonView::id).toList(),
                crew,
                "0/0/" + crew.size() + "/" + crew.size(),
                null,
                null,
                involved,
                false);
    }
}
