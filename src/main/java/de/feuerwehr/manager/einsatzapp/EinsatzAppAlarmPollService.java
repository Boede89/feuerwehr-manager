package de.feuerwehr.manager.einsatzapp;

import de.feuerwehr.manager.divera.DiveraAlarmDetailsMapper;
import de.feuerwehr.manager.divera.DiveraAlarmSummary;
import de.feuerwehr.manager.divera.DiveraAlarmsResponse;
import de.feuerwehr.manager.divera.DiveraService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Fallback, wenn DIVERA keinen Webhook liefert: offene Einsätze per API abfragen und Push auslösen.
 * Webhooks bleiben der schnellere Primärweg; Polling erkennt verpasste Alarme innerhalb weniger Sekunden.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EinsatzAppAlarmPollService {

    private final UnitRepository unitRepository;
    private final ModuleSettingsService moduleSettingsService;
    private final TestModeService testModeService;
    private final DiveraService diveraService;
    private final EinsatzAppSettingsService einsatzAppSettingsService;
    private final EinsatzAppPushService einsatzAppPushService;
    private final FcmPushClient fcmPushClient;

    public void pollAllUnits() {
        if (!fcmPushClient.isAvailable()) {
            return;
        }
        for (Unit unit : unitRepository.findActiveVisible(testModeService.isEnabled())) {
            long unitId = unit.getId();
            if (!moduleSettingsService.isEnabled(AppModule.EINSATZAPP, unitId)) {
                continue;
            }
            if (!einsatzAppSettingsService.isPushEnabled(unitId)) {
                continue;
            }
            try {
                pollUnit(unitId);
            } catch (Exception e) {
                log.warn("[Einsatz-App-Poll] unit={} Fehler: {}", unitId, e.getMessage());
            }
        }
    }

    public void pollUnit(long unitId) {
        DiveraAlarmsResponse response = diveraService.getAlarmsForUnit(unitId);
        if (!response.success()) {
            log.debug(
                    "[Einsatz-App-Poll] unit={} DIVERA-Abruf fehlgeschlagen: {}",
                    unitId,
                    response.message());
            return;
        }
        for (DiveraAlarmSummary summary : response.alarms()) {
            if (summary.id() <= 0 || summary.closed()) {
                continue;
            }
            // Geplante manuelle Einsätze (noch nicht gestartet) nicht per Push auslösen
            if (summary.manualDraft()) {
                continue;
            }
            DiveraAlarmDetailsMapper.fromSummary(summary, null)
                    .ifPresent(details -> {
                        if (summary.manualAlarm()) {
                            einsatzAppPushService.tryDispatchManualAlarm(unitId, details);
                        } else {
                            einsatzAppPushService.tryDispatchFromWebhook(unitId, details);
                        }
                    });
        }
    }
}
