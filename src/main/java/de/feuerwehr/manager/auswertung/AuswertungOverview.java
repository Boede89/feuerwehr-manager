package de.feuerwehr.manager.auswertung;

import java.util.List;

public record AuswertungOverview(
        int anzahlEinsaetze,
        int anzahlUebungen,
        int anzahlSonstiges,
        int aktivePersonen,
        double gesamtStunden,
        List<ChartSlice> topPersonen,
        List<ChartSlice> topStichworte) {}
