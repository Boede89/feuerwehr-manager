package de.feuerwehr.manager.dashboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Packt Widgets möglichst lückenfrei nach oben/links, Reihenfolge nach ursprünglicher Position. */
public final class DashboardLayoutPacker {

    private DashboardLayoutPacker() {}

    public static List<DashboardWidgetPlacement> packTight(List<DashboardWidgetPlacement> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<DashboardWidgetPlacement> ordered = source.stream()
                .sorted(Comparator.comparingInt(DashboardWidgetPlacement::y)
                        .thenComparingInt(DashboardWidgetPlacement::x)
                        .thenComparing(p -> p.type().name()))
                .toList();
        List<DashboardWidgetPlacement> placed = new ArrayList<>();
        int yLimit = Math.max(64, ordered.size() * DashboardWidgetPlacement.MAX_H);
        for (DashboardWidgetPlacement item : ordered) {
            int w = item.w();
            int h = item.h();
            int foundX = 0;
            int foundY = 0;
            boolean found = false;
            for (int y = 0; y <= yLimit && !found; y++) {
                for (int x = 0; x <= DashboardWidgetPlacement.COLS - w; x++) {
                    if (fits(placed, x, y, w, h)) {
                        foundX = x;
                        foundY = y;
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                foundY = DashboardLayoutService.nextFreeRow(placed);
                foundX = 0;
            }
            placed.add(new DashboardWidgetPlacement(item.type(), foundX, foundY, w, h, item.config()));
        }
        return List.copyOf(placed);
    }

    static boolean fits(List<DashboardWidgetPlacement> placed, int x, int y, int w, int h) {
        int x2 = x + w;
        int y2 = y + h;
        for (DashboardWidgetPlacement p : placed) {
            if (x < p.x() + p.w() && x2 > p.x() && y < p.y() + p.h() && y2 > p.y()) {
                return false;
            }
        }
        return true;
    }
}
