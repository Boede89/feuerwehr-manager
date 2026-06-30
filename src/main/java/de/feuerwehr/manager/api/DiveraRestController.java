package de.feuerwehr.manager.api;

import de.feuerwehr.manager.divera.DiveraAlarmsResponse;
import de.feuerwehr.manager.divera.DiveraService;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON-API für Android-App und externe Clients. */
@RestController
@RequestMapping("/api/v1/units/{unitId}/divera")
@RequiredArgsConstructor
public class DiveraRestController {

    private final DiveraService diveraService;
    private final AccessControlService accessControlService;
    private final UserPermissionService userPermissionService;
    private final ModuleSettingsService moduleSettingsService;

    @GetMapping("/alarms")
    public ResponseEntity<DiveraAlarmsResponse> alarms(
            @PathVariable long unitId, @AuthenticationPrincipal AppUserDetails actor) {
        if (actor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(DiveraAlarmsResponse.fail("Nicht angemeldet"));
        }
        try {
            accessControlService.requireUnitAccess(actor, unitId);
            if (!moduleSettingsService.isEnabled(AppModule.EINSATZAPP, unitId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(DiveraAlarmsResponse.fail("Modul Einsatz-App ist nicht aktiv"));
            }
            userPermissionService.requirePermission(actor, unitId, "einsatzapp.read");
            return ResponseEntity.ok(diveraService.getAlarmsForUnit(unitId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(DiveraAlarmsResponse.fail(e.getMessage()));
        }
    }
}
