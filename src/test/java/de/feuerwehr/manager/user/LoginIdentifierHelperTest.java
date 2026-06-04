package de.feuerwehr.manager.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoginIdentifierHelperTest {

    @Test
    void normalize_lowercasesEmail() {
        assertEquals("max@feuerwehr.de", LoginIdentifierHelper.normalize("  Max@Feuerwehr.DE "));
    }

    @Test
    void normalize_keepsUsernameCase() {
        assertEquals("M.Mustermann", LoginIdentifierHelper.normalize(" M.Mustermann "));
    }

    @Test
    void looksLikeEmail() {
        assertTrue(LoginIdentifierHelper.looksLikeEmail("a@b.de"));
        assertFalse(LoginIdentifierHelper.looksLikeEmail("m.mustermann"));
    }
}
