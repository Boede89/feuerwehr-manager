package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.unit.UnitDiveraSettings;
import de.feuerwehr.manager.unit.UnitDiveraSettingsRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiveraWebhookService {

    private final UnitDiveraSettingsRepository diveraSettingsRepository;
    private final ObjectMapper objectMapper;

    public enum WebhookStatus {
        ACCEPTED,
        DUPLICATE,
        FORBIDDEN,
        BAD_REQUEST
    }

    public record WebhookOutcome(WebhookStatus status, String externalId, String message) {}

    /**
     * Simuliert einen DIVERA-Webhook im Testmodus (kein Aufruf nach DIVERA, keine Secret-Prüfung).
     */
    public WebhookOutcome handleTestWebhook(long unitId, String rawBody) {
        Optional<UnitDiveraSettings> cfgOpt = diveraSettingsRepository.findByUnitId(unitId);
        if (cfgOpt.isEmpty()) {
            return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "Keine Divera-Einstellungen für diese Einheit");
        }
        if (rawBody == null || rawBody.isBlank()) {
            return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "JSON fehlt");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            log.info("[Divera-Webhook-Test] unit={} payload={}", unitId, root);
            String externalId = extractExternalId(root);
            return new WebhookOutcome(
                    WebhookStatus.ACCEPTED,
                    externalId,
                    "Test-Webhook verarbeitet (nur lokal, kein DIVERA-Aufruf)");
        } catch (Exception e) {
            log.error("[Divera-Webhook-Test] JSON-Fehler unit={}: {}", unitId, e.getMessage());
            return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "Ungültiges JSON: " + e.getMessage());
        }
    }

    public WebhookOutcome handleWebhook(long unitId, String secretFromQuery, String secretFromHeader, String rawBody) {
        Optional<UnitDiveraSettings> cfgOpt = diveraSettingsRepository.findByUnitId(unitId);
        if (cfgOpt.isEmpty()) {
            return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "Unbekannte Einheit");
        }
        UnitDiveraSettings cfg = cfgOpt.get();
        String stored = cfg.getWebhookSecret() != null ? cfg.getWebhookSecret().trim() : "";
        if (!stored.isEmpty()) {
            String provided = firstNonBlank(secretFromQuery, secretFromHeader);
            if (provided == null || !stored.equals(provided)) {
                log.warn("[Divera-Webhook] unit={} Secret ungültig", unitId);
                return new WebhookOutcome(WebhookStatus.FORBIDDEN, null, "Ungültiges Webhook-Secret");
            }
        }

        if (rawBody == null || rawBody.isBlank()) {
            return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "Leerer Body");
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            log.info("[Divera-Webhook] unit={} payload={}", unitId, root.toString());
            String externalId = extractExternalId(root);
            // Persistenz als Einsatzbericht folgt mit Modul „Berichte“
            return new WebhookOutcome(WebhookStatus.ACCEPTED, externalId, "Webhook empfangen");
        } catch (Exception e) {
            log.error("[Divera-Webhook] JSON-Fehler unit={}: {}", unitId, e.getMessage());
            return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "Ungültiges JSON");
        }
    }

    private static String extractExternalId(JsonNode root) {
        JsonNode alarm = root.has("data") ? root.path("data") : root;
        if (alarm.has("alarm")) {
            alarm = alarm.path("alarm");
        }
        String foreign = alarm.path("foreign_id").asText("");
        if (!foreign.isBlank()) {
            return foreign;
        }
        long id = alarm.path("id").asLong(0);
        if (id > 0) {
            return "divera:" + id;
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }
}
