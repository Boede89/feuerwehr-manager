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
        DiveraAlarmsResponse apiResponse = diveraSettingsRepository
                .findByUnitId(unitId)
                .map(cfg -> diveraApiClient.fetchAlarms(cfg.getApiBaseUrl(), cfg.getAccessKey()))
                .orElse(DiveraAlarmsResponse.fail("Keine Divera-Einstellungen für diese Einheit"));

        if (testModeService.isEnabled()) {
            diveraAlarmSampleService.captureFromApiResponse(unitId, apiResponse);
        }

        DiveraAlarmsResponse visible = testModeService.isEnabled() ? apiResponse : withoutClosedAlarms(apiResponse);

        List<DiveraAlarmSummary> testAlarms = testDiveraAlarmService.listOpenSummariesForUnit(unitId);
        if (testAlarms.isEmpty()) {
            return visible;
        }
        return DiveraAlarmsResponse.ok(testDiveraAlarmService.mergeInto(visible, testAlarms));
    }

    private static DiveraAlarmsResponse withoutClosedAlarms(DiveraAlarmsResponse response) {
        if (!response.success()) {
            return response;
        }
        List<DiveraAlarmSummary> open =
                response.alarms().stream().filter(a -> !a.closed()).toList();
        return DiveraAlarmsResponse.ok(open);
    }

    /** Nur nicht geschlossene Einsätze (für Dashboard / später Push-Hook). */
    @Transactional(readOnly = true)
    public List<DiveraAlarmSummary> getOpenAlarmsForUnit(long unitId) {
        return getAlarmsForUnit(unitId).alarms().stream().filter(a -> !a.closed()).toList();
    }
}
