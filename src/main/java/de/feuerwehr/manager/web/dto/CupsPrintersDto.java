package de.feuerwehr.manager.web.dto;

import de.feuerwehr.manager.print.CupsPrintService;
import java.util.List;

public record CupsPrintersDto(boolean ok, boolean cupsAvailable, List<CupsPrintService.CupsPrinterOption> printers, String message) {

    public static CupsPrintersDto success(boolean cupsAvailable, List<CupsPrintService.CupsPrinterOption> printers) {
        return new CupsPrintersDto(true, cupsAvailable, printers, null);
    }

    public static CupsPrintersDto success(
            boolean cupsAvailable, List<CupsPrintService.CupsPrinterOption> printers, String message) {
        return new CupsPrintersDto(true, cupsAvailable, printers, message);
    }

    public static CupsPrintersDto failure(String message) {
        return new CupsPrintersDto(false, false, List.of(), message);
    }
}
