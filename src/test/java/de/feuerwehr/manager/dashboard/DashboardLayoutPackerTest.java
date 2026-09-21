package de.feuerwehr.manager.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardLayoutPackerTest {

    @Test
    void packTightFillsGapWhenMiddleWidgetRemoved() {
        List<DashboardWidgetPlacement> remaining = List.of(
                new DashboardWidgetPlacement(DashboardWidgetType.MY_STATS, 0, 0, 6, 5),
                // originally at y=5 with a removed widget above would leave gap; here second was at y=10
                new DashboardWidgetPlacement(DashboardWidgetType.DIVERA, 0, 10, 8, 8));

        List<DashboardWidgetPlacement> packed = DashboardLayoutPacker.packTight(remaining);

        assertEquals(2, packed.size());
        assertEquals(0, packed.get(0).y());
        assertEquals(0, packed.get(0).x());
        // DIVERA should move up next to / below MY_STATS without large gap
        assertTrue(packed.get(1).y() <= 5);
        assertTrue(DashboardLayoutPacker.fits(
                List.of(packed.get(0)), packed.get(1).x(), packed.get(1).y(), packed.get(1).w(), packed.get(1).h()));
    }

    @Test
    void packTightPlacesSideBySideWhenSpaceAllows() {
        List<DashboardWidgetPlacement> remaining = List.of(
                new DashboardWidgetPlacement(DashboardWidgetType.QUICK_RESERVE, 0, 0, 3, 5),
                new DashboardWidgetPlacement(DashboardWidgetType.QUICK_BUG_REPORT, 6, 0, 3, 5));

        List<DashboardWidgetPlacement> packed = DashboardLayoutPacker.packTight(remaining);

        assertEquals(0, packed.get(0).y());
        assertEquals(0, packed.get(0).x());
        assertEquals(0, packed.get(1).y());
        assertEquals(3, packed.get(1).x());
    }
}
