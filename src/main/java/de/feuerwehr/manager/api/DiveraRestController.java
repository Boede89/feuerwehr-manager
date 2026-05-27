package de.feuerwehr.manager.api;

import de.feuerwehr.manager.divera.DiveraAlarmsResponse;
import de.feuerwehr.manager.divera.DiveraService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON-API für spätere Android-App und externe Clients. */
@RestController
@RequestMapping("/api/v1/units/{unitId}/divera")
@RequiredArgsConstructor
public class DiveraRestController {

    private final DiveraService diveraService;

    @GetMapping("/alarms")
    public DiveraAlarmsResponse alarms(@PathVariable long unitId) {
        return diveraService.getAlarmsForUnit(unitId);
    }
}
