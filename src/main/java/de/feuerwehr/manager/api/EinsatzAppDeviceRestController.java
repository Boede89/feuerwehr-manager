package de.feuerwehr.manager.api;

import de.feuerwehr.manager.einsatzapp.EinsatzAppSettingsService;
import de.feuerwehr.manager.einsatzapp.EinsatzappDeviceToken;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/einsatzapp")
@RequiredArgsConstructor
public class EinsatzAppDeviceRestController {

    private final EinsatzAppSettingsService einsatzAppSettingsService;
    private final AccessControlService accessControlService;
    private final UserPermissionService userPermissionService;
    private final ModuleSettingsService moduleSettingsService;

    public record RegisterDeviceRequest(long unitId, String fcmToken, String deviceLabel, String platform) {}

    public record UnregisterDeviceRequest(String fcmToken) {}

    @PostMapping("/devices")
    public ResponseEntity<Map<String, Object>> registerDevice(
            @AuthenticationPrincipal AppUserDetails actor, @RequestBody RegisterDeviceRequest body) {
        if (actor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "Nicht angemeldet"));
        }
        if (body == null || body.unitId() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Einheit fehlt"));
        }
        try {
            accessControlService.requireUnitAccess(actor, body.unitId());
            if (!moduleSettingsService.isEnabled(AppModule.EINSATZAPP, body.unitId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "message", "Modul Einsatz-App ist nicht aktiv"));
            }
            userPermissionService.requirePermission(actor, body.unitId(), "einsatzapp.read");
            EinsatzappDeviceToken row = einsatzAppSettingsService.registerDevice(
                    actor.getUserId(), body.unitId(), body.fcmToken(), body.deviceLabel(), body.platform());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "deviceId", row.getId(),
                    "message", "Gerät registriert"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/devices")
    public ResponseEntity<Map<String, Object>> unregisterDevice(
            @AuthenticationPrincipal AppUserDetails actor, @RequestBody UnregisterDeviceRequest body) {
        if (actor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "Nicht angemeldet"));
        }
        if (body == null || body.fcmToken() == null || body.fcmToken().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "FCM-Token fehlt"));
        }
        einsatzAppSettingsService.unregisterDevice(actor.getUserId(), body.fcmToken());
        return ResponseEntity.ok(Map.of("success", true, "message", "Gerät abgemeldet"));
    }

    @GetMapping("/devices")
    public ResponseEntity<Map<String, Object>> listDevices(
            @AuthenticationPrincipal AppUserDetails actor, @RequestParam long unitId) {
        if (actor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "Nicht angemeldet"));
        }
        try {
            accessControlService.requireUnitAccess(actor, unitId);
            userPermissionService.requirePermission(actor, unitId, "einsatzapp.read");
            List<EinsatzappDeviceToken> devices = einsatzAppSettingsService.listDevicesForUser(actor.getUserId(), unitId);
            List<Map<String, Object>> payload = devices.stream()
                    .map(d -> Map.<String, Object>of(
                            "id", d.getId(),
                            "deviceLabel", d.getDeviceLabel() != null ? d.getDeviceLabel() : "",
                            "platform", d.getPlatform(),
                            "lastSeenAt", d.getLastSeenAt() != null ? d.getLastSeenAt().toString() : ""))
                    .toList();
            return ResponseEntity.ok(Map.of("success", true, "devices", payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
