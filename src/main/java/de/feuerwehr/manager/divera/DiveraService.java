package de.feuerwehr.manager.divera;

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

    @Transactional(readOnly = true)
    public DiveraAlarmsResponse getAlarmsForUnit(long unitId) {
        DiveraAlarmsResponse apiResponse = diveraSettingsRepository
                .findByUnitId(unitId)
                .map(cfg -> diveraApiClient.fetchOpenAlarms(cfg.getApiBaseUrl(), cfg.getAccessKey()))
                .orElse(DiveraAlarmsResponse.fail("Keine Divera-Einstellungen für diese Einheit"));

        List<DiveraAlarmSummary> testAlarms = testDiveraAlarmService.listOpenSummariesForUnit(unitId);
        if (testAlarms.isEmpty()) {
            return apiResponse;
        }
        return DiveraAlarmsResponse.ok(testDiveraAlarmService.mergeInto(apiResponse, testAlarms));
    }

    /** Nur nicht geschlossene Einsätze (für Dashboard / später Push-Hook). */
    @Transactional(readOnly = true)
    public List<DiveraAlarmSummary> getOpenAlarmsForUnit(long unitId) {
        return getAlarmsForUnit(unitId).alarms().stream().filter(a -> !a.closed()).toList();
    }
}
