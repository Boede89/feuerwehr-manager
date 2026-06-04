package de.feuerwehr.manager.unit;

import java.util.List;

/** Ein Navigationsmodul mit den vergebbaren Berechtigungsstufen. */
public record RoleModuleDefinition(String key, String label, boolean planned, List<RolePermissionLevel> levels) {}
