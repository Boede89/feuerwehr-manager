package de.feuerwehr.manager.auswertung;

import java.time.LocalDate;
import java.time.LocalTime;

public record AuswertungFilter(
        int year,
        LocalDate from,
        LocalDate to,
        AuswertungTypFilter typ,
        String thema,
        String stichwort,
        Long personId,
        Long vehicleId,
        LocalTime timeFrom,
        LocalTime timeTo) {

    public boolean hasTimeFilter() {
        return timeFrom != null || timeTo != null;
    }
}
