package de.feuerwehr.manager.dashboard;

/** Katalog-Eintrag für „Widget hinzufügen“. */
public record DashboardWidgetCatalogItem(
        String id, String label, String description, boolean alreadyActive) {}
