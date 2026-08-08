package de.feuerwehr.manager.dashboard;

/** Breite eines Startseiten-Widgets im 12-Spalten-Raster. */
public enum DashboardWidgetSize {
    NARROW(4, "Schmal"),
    HALF(6, "Halb"),
    WIDE(8, "Breit"),
    FULL(12, "Ganz");

    private final int columns;
    private final String label;

    DashboardWidgetSize(int columns, String label) {
        this.columns = columns;
        this.label = label;
    }

    public int columns() {
        return columns;
    }

    public String label() {
        return label;
    }

    public String cssModifier() {
        return name().toLowerCase();
    }

    public DashboardWidgetSize next() {
        return switch (this) {
            case NARROW -> HALF;
            case HALF -> WIDE;
            case WIDE -> FULL;
            case FULL -> NARROW;
        };
    }

    public static DashboardWidgetSize fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return DashboardWidgetSize.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static DashboardWidgetSize defaultFor(DashboardWidgetType type) {
        if (type == null) {
            return FULL;
        }
        return switch (type) {
            case TERMINE -> NARROW;
            case MY_STATS -> HALF;
            case UNIT_OVERVIEW -> FULL;
            case DIVERA, PLANNED_ALARMS -> WIDE;
        };
    }
}
