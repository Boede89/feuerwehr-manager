package de.feuerwehr.manager.dashboard;

import java.util.List;

/** Aufbereitete Atemschutz-Kennzahl für das Dashboard-Widget. */
public record DashboardAtemschutzMetricView(
        String key,
        String label,
        String cssModifier,
        String filter,
        int count,
        boolean showNames,
        List<String> names) {}
