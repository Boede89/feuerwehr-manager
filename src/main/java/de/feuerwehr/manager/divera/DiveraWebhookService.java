package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.divera.DiveraAlarmDetailsMapper;
import de.feuerwehr.manager.einsatzapp.EinsatzAppPushService;
import de.feuerwehr.manager.settings.TestModeService;
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
    private final TestDiveraAlarmService testDiveraAlarmService;
    private final DiveraAlarmSampleService diveraAlarmSampleService;
    private final DiveraEinsatzberichtSyncService einsatzberichtSyncService;
    private final TestModeService testModeService;
    private final EinsatzAppPushService einsatzAppPushService;
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
     * Speichert den Alarm für die Startseite; beim Beenden des Testmodus werden alle Testalarme gelöscht.
     */
    public WebhookOutcome handleTestWebhook(long unitId, String rawBody, boolean sendPush) {
        WebhookOutcome outcome = testDiveraAlarmService.ingestTestWebhook(unitId, rawBody);
        if (outcome.status() == WebhookStatus.ACCEPTED) {
            diveraAlarmSampleService.captureFromWebhook(unitId, rawBody);
            if (sendPush) {
                tryDispatchEinsatzAppPush(unitId, rawBody);
            }
            log.info("[Divera-Webhook-Test] unit={} externalId={} push={}", unitId, outcome.externalId(), sendPush);
        }
        return outcome;
    }

    /** Wie {@link #tryDispatchEinsatzAppPush}, aber der Alarm gilt als offen (Testalarm „Einsatz starten“). */
    public void tryDispatchEinsatzAppPushForStartedSample(long unitId, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            einsatzAppPushService.recordSkipped(unitId, "Übersprungen: Leerer Webhook-Body");
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            DiveraAlarmJsonParser.forceAlarmOpen(root);
            tryDispatchEinsatzAppPush(unitId, objectMapper.writeValueAsString(root));
        } catch (Exception e) {
            einsatzAppPushService.recordSkipped(
                    unitId, "Übersprungen: JSON ungültig — " + e.getMessage());
            log.debug("[Einsatz-App] Push für gestarteten Test-Einsatz unit={}: {}", unitId, e.getMessage());
        }
    }

    /** Löst einen Einsatz-App-Push aus dem Webhook-JSON aus (z. B. Testalarm „Einsatz starten“). */
    public void tryDispatchEinsatzAppPush(long unitId, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            einsatzAppPushService.recordSkipped(unitId, "Übersprungen: Leerer Webhook-Body");
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            DiveraAlarmDetailsMapper.fromWebhookJson(root)
                    .ifPresentOrElse(
                            details -> einsatzAppPushService.tryDispatchFromWebhook(unitId, details),
                            () -> einsatzAppPushService.recordSkipped(unitId, "Übersprungen: Kein Alarm im JSON"));
        } catch (Exception e) {
            einsatzAppPushService.recordSkipped(
                    unitId, "Übersprungen: JSON ungültig — " + e.getMessage());
            log.debug("[Einsatz-App] Push aus Webhook nicht auslösbar unit={}: {}", unitId, e.getMessage());
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
            boolean sampleSaved = false;
            if (testModeService.isEnabled()) {
                sampleSaved = diveraAlarmSampleService.captureFromWebhook(unitId, rawBody);
            }
            boolean draftCreated = einsatzberichtSyncService.syncFromWebhook(unitId, rawBody);
            dispatchEinsatzAppPush(unitId, rawBody);
            String externalId = extractExternalId(root);
            String message = sampleSaved
                    ? (draftCreated
                            ? "Webhook empfangen — Beispiel gespeichert, Einsatzbericht-Entwurf angelegt"
                            : "Webhook empfangen — Beispiel-Einsatz im Testmodus gespeichert")
                    : (draftCreated ? "Webhook empfangen — Einsatzbericht-Entwurf angelegt" : "Webhook empfangen");
            return new WebhookOutcome(WebhookStatus.ACCEPTED, externalId, message);
        } catch (Exception e) {
            log.error("[Divera-Webhook] JSON-Fehler unit={}: {}", unitId, e.getMessage());
            return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "Ungültiges JSON");
        }
    }

    private void dispatchEinsatzAppPush(long unitId, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return;
        }
        tryDispatchEinsatzAppPush(unitId, rawBody);
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
