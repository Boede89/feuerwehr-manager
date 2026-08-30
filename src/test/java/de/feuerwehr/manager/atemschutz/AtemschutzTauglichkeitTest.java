package de.feuerwehr.manager.atemschutz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.feuerwehr.manager.atemschutz.AtemschutzService.FitnessStatusView;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AtemschutzTauglichkeitTest {

    @Test
    void warningWithoutExpiryStillCountsAsTauglich() {
        Map<AtemschutzFitnessType, FitnessStatusView> summaries = summaries(
                AtemschutzFitnessLevel.WARN,
                AtemschutzFitnessLevel.OK,
                AtemschutzFitnessLevel.OK);
        assertEquals(
                CarrierTauglichkeitStatus.WARNUNG,
                AtemschutzService.computeTauglichkeit(summaries, AtemschutzCarrierStatus.ACTIVE));
        assertTrue(AtemschutzService.isOverallTauglich(summaries, AtemschutzCarrierStatus.ACTIVE));
        assertTrue(CarrierTauglichkeitStatus.WARNUNG.countsAsTauglich());
        assertEquals("Tauglich", CarrierTauglichkeitStatus.WARNUNG.operationalLabel());
    }

    @Test
    void expiredDateIsNotTauglich() {
        Map<AtemschutzFitnessType, FitnessStatusView> summaries = summaries(
                AtemschutzFitnessLevel.OVERDUE,
                AtemschutzFitnessLevel.OK,
                AtemschutzFitnessLevel.OK);
        assertEquals(
                CarrierTauglichkeitStatus.NICHT_TAUGLICH,
                AtemschutzService.computeTauglichkeit(summaries, AtemschutzCarrierStatus.ACTIVE));
        assertFalse(AtemschutzService.isOverallTauglich(summaries, AtemschutzCarrierStatus.ACTIVE));
    }

    @Test
    void onlyExpiredExerciseIsSeparateStatus() {
        Map<AtemschutzFitnessType, FitnessStatusView> summaries = summaries(
                AtemschutzFitnessLevel.OK,
                AtemschutzFitnessLevel.OVERDUE,
                AtemschutzFitnessLevel.OK);
        assertEquals(
                CarrierTauglichkeitStatus.UEBUNG_ABGELAUFEN,
                AtemschutzService.computeTauglichkeit(summaries, AtemschutzCarrierStatus.ACTIVE));
        assertFalse(CarrierTauglichkeitStatus.UEBUNG_ABGELAUFEN.countsAsTauglich());
    }

    private static Map<AtemschutzFitnessType, FitnessStatusView> summaries(
            AtemschutzFitnessLevel g26, AtemschutzFitnessLevel uebung, AtemschutzFitnessLevel strecke) {
        Map<AtemschutzFitnessType, FitnessStatusView> map = new EnumMap<>(AtemschutzFitnessType.class);
        LocalDate until = LocalDate.now().plusDays(10);
        map.put(AtemschutzFitnessType.G26_UNTERSUCHUNG, new FitnessStatusView(g26, until, until.minusYears(1)));
        map.put(AtemschutzFitnessType.UEBUNG, new FitnessStatusView(uebung, until, until.minusMonths(6)));
        map.put(AtemschutzFitnessType.STRECKEN, new FitnessStatusView(strecke, until, until.minusYears(1)));
        return map;
    }
}
