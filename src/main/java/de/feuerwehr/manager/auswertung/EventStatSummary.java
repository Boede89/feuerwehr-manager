package de.feuerwehr.manager.auswertung;

import java.util.List;

public record EventStatSummary(
        int anzahlEinsaetze,
        int anzahlUebungen,
        int anzahlSonstiges,
        int anzahlGesamt,
        double gesamtStunden,
        double durchschnittPersonen,
        List<ChartSlice> stichworte,
        List<ChartSlice> themen) {}
