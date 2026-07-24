package de.feuerwehr.manager.auswertung;

import java.util.List;

public record EquipmentStatRow(
        String equipmentName,
        String categoryName,
        int anzahl,
        List<ChartSlice> vehicles) {}
