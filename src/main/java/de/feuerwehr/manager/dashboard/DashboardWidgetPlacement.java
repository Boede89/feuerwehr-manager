package de.feuerwehr.manager.dashboard;

/** Ein Widget-Eintrag im persönlichen Dashboard-Layout. */
public record DashboardWidgetPlacement(DashboardWidgetType type, DashboardWidgetSize size) {

    public DashboardWidgetPlacement {
        if (type == null) {
            throw new IllegalArgumentException("type");
        }
        if (size == null) {
            size = DashboardWidgetSize.defaultFor(type);
        }
    }

    public static DashboardWidgetPlacement of(DashboardWidgetType type) {
        return new DashboardWidgetPlacement(type, DashboardWidgetSize.defaultFor(type));
    }

    public String sizeCssClass() {
        return "dashboard-widget--size-" + size.cssModifier();
    }
}
