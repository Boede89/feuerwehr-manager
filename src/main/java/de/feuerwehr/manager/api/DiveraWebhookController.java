package de.feuerwehr.manager.api;

import de.feuerwehr.manager.divera.DiveraWebhookService;
import de.feuerwehr.manager.divera.DiveraWebhookService.WebhookOutcome;
import de.feuerwehr.manager.divera.DiveraWebhookService.WebhookStatus;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class DiveraWebhookController {

    private final DiveraWebhookService diveraWebhookService;

    @PostMapping("/divera")
    public ResponseEntity<Map<String, Object>> receiveDivera(
            @RequestParam long unit,
            @RequestParam(required = false) String secret,
            @RequestHeader(value = "X-Webhook-Secret", required = false) String headerSecret,
            @RequestBody(required = false) String body) {
        WebhookOutcome outcome = diveraWebhookService.handleWebhook(unit, secret, headerSecret, body);
        return switch (outcome.status()) {
            case FORBIDDEN -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("status", "forbidden", "message", outcome.message()));
            case BAD_REQUEST -> ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", outcome.message()));
            case DUPLICATE -> ResponseEntity.ok(Map.of(
                    "status", "duplicate",
                    "external_id", outcome.externalId() != null ? outcome.externalId() : ""));
            case ACCEPTED -> ResponseEntity.ok(Map.of(
                    "status", "accepted",
                    "external_id", outcome.externalId() != null ? outcome.externalId() : "",
                    "message", outcome.message()));
        };
    }
}
