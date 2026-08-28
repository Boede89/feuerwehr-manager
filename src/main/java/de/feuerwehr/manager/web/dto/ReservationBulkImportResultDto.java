package de.feuerwehr.manager.web.dto;

import java.util.List;

public record ReservationBulkImportResultDto(
        boolean ok, String message, int imported, int skipped, List<String> details) {

    public static ReservationBulkImportResultDto success(int imported, int skipped, List<String> details) {
        String message = imported + " Reservierung(en) importiert"
                + (skipped > 0 ? ", " + skipped + " übersprungen" : "") + ".";
        return new ReservationBulkImportResultDto(true, message, imported, skipped, List.copyOf(details));
    }

    public static ReservationBulkImportResultDto failure(String message) {
        return new ReservationBulkImportResultDto(false, message, 0, 0, List.of());
    }
}
