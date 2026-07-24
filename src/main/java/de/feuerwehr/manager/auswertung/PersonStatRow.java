package de.feuerwehr.manager.auswertung;

import java.util.List;

public record PersonStatRow(
        long personId,
        String displayName,
        int einsaetze,
        int uebungen,
        int sonstiges,
        int teilnahmen,
        double stunden,
        int alsMaschinist,
        int alsEinheitsfuehrer,
        String letzteTeilnahme,
        double quotePercent,
        List<ChartSlice> fahrzeuge) {}
