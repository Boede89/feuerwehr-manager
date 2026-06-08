package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.berichte.EinsatzberichtService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiveraEinsatzberichtSyncService {

    private final DiveraService diveraService;
    private final EinsatzberichtService einsatzberichtService;
    private final ModuleSettingsService moduleSettingsService;
    private final ObjectMapper objectMapper;

    public record SyncResult(boolean success, int created, int skipped, String message) {}

    @Transactional
    public SyncResult syncAlarmsForUnit(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.BERICHTE, unitId)) {
            return new SyncResult(false, 0, 0, "Modul Berichte ist deaktiviert.");
        }
        DiveraAlarmsResponse response = diveraService.fetchAllAlarmsForBerichteSync(unitId);
        if (!response.success()) {
            return new SyncResult(false, 0, 0, response.message());
        }
        int created = 0;
        int skipped = 0;
        for (DiveraAlarmSummary alarm : response.alarms()) {
            if (ensureDraftAndSyncPersonnel(unitId, alarm, response.rawJsonByAlarmId())) {
                created++;
            } else {
                skipped++;
            }
        }
        String message = created == 0
                ? (skipped == 0
                        ? "Keine DIVERA-Einsätze zum Übernehmen."
                        : skipped + " Einsatz/Einsätze bereits als Bericht vorhanden oder nicht verwertbar.")
                : created + " Einsatzbericht/Einsatzberichte als Entwurf angelegt."
                        + (skipped > 0 ? " (" + skipped + " übersprungen)" : "");
        log.info("[Divera→Berichte] unit={} created={} skipped={}", unitId, created, skipped);
        return new SyncResult(true, created, skipped, message);
    }

    @Transactional
    public boolean syncFromWebhook(long unitId, String rawBody) {
        if (!moduleSettingsService.isEnabled(AppModule.BERICHTE, unitId)) {
            return false;
        }
        if (rawBody == null || rawBody.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            return DiveraAlarmDetailsMapper.fromWebhookJson(root)
                    .map(details -> {
                        boolean created = einsatzberichtService.createDraftFromDiveraIfMissing(unitId, details);
                        einsatzberichtService.refreshDiveraPersonnelFromDetails(unitId, details);
                        return created;
                    })
                    .orElse(false);
        } catch (Exception e) {
            log.warn("[Divera→Berichte] Webhook-Sync fehlgeschlagen unit={}: {}", unitId, e.getMessage());
            return false;
        }
    }

    private boolean ensureDraftAndSyncPersonnel(
            long unitId, DiveraAlarmSummary alarm, Map<Long, String> rawJsonByAlarmId) {
        JsonNode root = null;
        String raw = rawJsonByAlarmId != null ? rawJsonByAlarmId.get(alarm.id()) : null;
        if (raw != null && !raw.isBlank()) {
            try {
                root = objectMapper.readTree(raw);
            } catch (Exception ignored) {
                root = null;
            }
        }
        return DiveraAlarmDetailsMapper.fromSummary(alarm, root)
                .map(details -> {
                    boolean created = einsatzberichtService.createDraftFromDiveraIfMissing(unitId, details);
                    einsatzberichtService.refreshDiveraPersonnelFromDetails(unitId, details);
                    return created;
                })
                .orElse(false);
    }
}
