package de.feuerwehr.manager.divera;

import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.UnitDiveraSettingsRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DiveraService {

    private final DiveraApiClient diveraApiClient;
    private final UnitDiveraSettingsRepository diveraSettingsRepository;
    private final TestDiveraAlarmService testDiveraAlarmService;
    private final DiveraAlarmSampleService diveraAlarmSampleService;
    private final TestModeService testModeService;

    @Transactional(readOnly = true)
    public DiveraAlarmsResponse getAlarmsForUnit(long unitId) {
        if (testModeService.isEnabled()) {
            List<DiveraAlarmSummary> running = testDiveraAlarmService.listOpenSummariesForUnit(unitId);
            return DiveraAlarmsResponse.ok(running);
        }

        DiveraAlarmsResponse apiResponse = diveraSettingsRepository
                .findByUnitId(unitId)
                .map(cfg -> diveraApiClient.fetchAlarms(cfg.getApiBaseUrl(), cfg.getAccessKey()))
                .orElse(DiveraAlarmsResponse.fail("Keine Divera-Einstellungen für diese Einheit"));

        return withoutClosedAlarms(apiResponse);
    }

    private static DiveraAlarmsResponse withoutClosedAlarms(DiveraAlarmsResponse response) {
        if (!response.success()) {
            return response;
        }
        List<DiveraAlarmSummary> open =
                response.alarms().stream().filter(a -> !a.closed()).toList();
        return DiveraAlarmsResponse.ok(open);
    }

    /**
     * Holt aktuelle DIVERA-Einsätze und speichert sie als Beispiel-Payloads (nur Testmodus).
     * Wird beim Öffnen des Testalarm-Reiters ausgeführt, damit keine Startseite nötig ist.
     */
    @Transactional
    public void syncAlarmSamplesForUnit(long unitId) {
        if (!testModeService.isEnabled()) {
            return;
        }
        diveraSettingsRepository
                .findByUnitId(unitId)
                .ifPresent(cfg -> {
                    DiveraAlarmsResponse apiResponse =
                            diveraApiClient.fetchAlarms(cfg.getApiBaseUrl(), cfg.getAccessKey());
                    if (apiResponse.success()) {
                        diveraAlarmSampleService.captureFromApiResponse(unitId, apiResponse);
                    }
                });
    }

    /** Nur nicht geschlossene Einsätze (für Dashboard / später Push-Hook). */
    @Transactional(readOnly = true)
    public List<DiveraAlarmSummary> getOpenAlarmsForUnit(long unitId) {
        return getAlarmsForUnit(unitId).alarms().stream().filter(a -> !a.closed()).toList();
    }
}
