package de.feuerwehr.manager.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class FeuerwehrErrorControllerTest {

    @Test
    void forbiddenExplainsMissingPermission() {
        FeuerwehrErrorController.ErrorPageView view = FeuerwehrErrorController.viewForStatus(403);
        assertEquals("Kein Zugriff", view.title());
        assertEquals("Sie haben keine Berechtigung für diese Seite.", view.message());
        assertFalse(view.showStatusCode());
    }

    @Test
    void notFoundHasOwnMessage() {
        FeuerwehrErrorController.ErrorPageView view = FeuerwehrErrorController.viewForStatus(404);
        assertEquals("Seite nicht gefunden", view.title());
        assertFalse(view.showStatusCode());
    }
}
