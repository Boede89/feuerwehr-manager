package de.feuerwehr.manager.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RfidCardUidNormalizerTest {

    @Test
    void normalizesHexWithSeparators() {
        assertEquals("A1B2C3D4", RfidCardUidNormalizer.normalize("a1:b2-c3 d4"));
    }

    @Test
    void validatesHexLength() {
        assertTrue(RfidCardUidNormalizer.isValid("ABCD"));
        assertFalse(RfidCardUidNormalizer.isValid("ABC"));
        assertFalse(RfidCardUidNormalizer.isValid("GHIJ"));
    }
}
