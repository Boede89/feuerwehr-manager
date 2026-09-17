package de.feuerwehr.manager.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Leichter Heartbeat für den Web-Idle-Logout: aktualisiert die letzte Aktivität
 * (über {@code IdleLogoutFilter}), ohne HTML-Seitenkontext zu laden.
 */
@RestController
public class WebSessionKeepaliveController {

    @PostMapping("/session/keepalive")
    public ResponseEntity<Void> keepalive() {
        return ResponseEntity.noContent().build();
    }
}
