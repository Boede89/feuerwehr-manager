package de.feuerwehr.manager.dashboard;

/**
 * Freie Platzierung eines Widgets auf dem 12-Spalten-Raster.
 * {@code x}/{@code y} = Startzelle (0-basiert), {@code w}/{@code h} = Span.
 */
public record DashboardWidgetPlacement(DashboardWidgetType type, int x, int y, int w, int h) {

    public static final int COLS = 12;
    public static final int MIN_W = 2;
    public static final int MIN_H = 3;
    public static final int MAX_H = 24;

    public DashboardWidgetPlacement {
        if (type == null) {
            throw new IllegalArgumentException("type");
        }
        x = clamp(x, 0, COLS - 1);
        y = Math.max(0, y);
        w = clamp(w, MIN_W, COLS);
        h = clamp(h, MIN_H, MAX_H);
        if (x + w > COLS) {
            x = Math.max(0, COLS - w);
        }
    }

    public static DashboardWidgetPlacement of(DashboardWidgetType type) {
        return defaultFor(type, 0);
    }

    public static DashboardWidgetPlacement defaultFor(DashboardWidgetType type, int rowHint) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case MY_STATS -> new DashboardWidgetPlacement(type, 0, rowHint, 6, 5);
            case DIVERA -> new DashboardWidgetPlacement(type, 0, Math.max(rowHint, 5), 8, 8);
            case TERMINE -> new DashboardWidgetPlacement(type, 8, Math.max(rowHint, 5), 4, 8);
            case PLANNED_ALARMS -> new DashboardWidgetPlacement(type, 0, rowHint, 8, 7);
            case UNIT_OVERVIEW -> new DashboardWidgetPlacement(type, 0, rowHint, 12, 5);
        };
    }

    /** Legacy-Größenname → Breite. */
    public static int widthFromLegacySize(String sizeId) {
        if (sizeId == null) {
            return 6;
        }
        return switch (sizeId.trim().toUpperCase()) {
            case "NARROW" -> 4;
            case "HALF" -> 6;
            case "WIDE" -> 8;
            case "FULL" -> 12;
            default -> 6;
        };
    }

    public String gridStyle() {
        return "grid-column:" + (x + 1) + " / span " + w + ";grid-row:" + (y + 1) + " / span " + h + ";";
    }

    public String cardModifierClass() {
        return switch (type) {
            case MY_STATS -> "widget-card--meine-statistik";
            case DIVERA, PLANNED_ALARMS -> "widget-card--einsatz";
            case TERMINE -> "widget-card--termine";
            case UNIT_OVERVIEW -> "widget-card--unit-overview";
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
