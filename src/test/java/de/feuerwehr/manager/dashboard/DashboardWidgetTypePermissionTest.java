package de.feuerwehr.manager.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.feuerwehr.manager.settings.AppModule;
import org.junit.jupiter.api.Test;

class DashboardWidgetTypePermissionTest {

    @Test
    void sensitiveWidgetsRequireModuleAccess() {
        assertEquals(AppModule.ATEMSCHUTZ, DashboardWidgetType.ATEMSCHUTZ.requiredModule());
        assertEquals(AppModule.BERICHTE, DashboardWidgetType.OPEN_REPORTS.requiredModule());
        assertEquals(AppModule.ATEMSCHUTZ, DashboardWidgetType.QUICK_ATEMSCHUTZ.requiredModule());
        assertEquals(AppModule.AUSWERTUNG, DashboardWidgetType.UNIT_OVERVIEW.requiredModule());
        assertEquals(AppModule.TERMINE, DashboardWidgetType.TERMINE.requiredModule());
    }

    @Test
    void publicQuickTilesRemainOpen() {
        assertNull(DashboardWidgetType.QUICK_BUG_REPORT.requiredModule());
        assertNull(DashboardWidgetType.QUICK_FORMS.requiredModule());
        assertNull(DashboardWidgetType.QUICK_RESERVE.requiredModule());
        assertNull(DashboardWidgetType.MY_STATS.requiredModule());
        assertNull(DashboardWidgetType.DIVERA.requiredModule());
        assertNotNull(DashboardWidgetType.PLANNED_ALARMS);
    }
}
