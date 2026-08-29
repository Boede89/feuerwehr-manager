package de.feuerwehr.manager.atemschutz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AtemschutzNotificationCategoryTest {

    @Test
    void fromFitnessTypeMapsAllNachweistypen() {
        assertEquals(
                AtemschutzNotificationCategory.G26,
                AtemschutzNotificationCategory.fromFitnessType(AtemschutzFitnessType.G26_UNTERSUCHUNG));
        assertEquals(
                AtemschutzNotificationCategory.UEBUNG,
                AtemschutzNotificationCategory.fromFitnessType(AtemschutzFitnessType.UEBUNG));
        assertEquals(
                AtemschutzNotificationCategory.STRECKEN,
                AtemschutzNotificationCategory.fromFitnessType(AtemschutzFitnessType.STRECKEN));
        assertEquals(
                AtemschutzNotificationCategory.CSA,
                AtemschutzNotificationCategory.fromFitnessType(AtemschutzFitnessType.CSA));
    }

    @Test
    void fromFitnessTypeRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> AtemschutzNotificationCategory.fromFitnessType(null));
    }
}
