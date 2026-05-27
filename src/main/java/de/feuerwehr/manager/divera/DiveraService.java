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

    @Transactional(readOnly = true)
    public DiveraAlarmsResponse getAlarmsForUnit(long unitId) {
        return diveraSettingsRepository
                .findByUnitId(unitId)
                .map(cfg -> diveraApiClient.fetchOpenAlarms(cfg.getApiBaseUrl(), cfg.getAccessKey()))
                .orElse(DiveraAlarmsResponse.fail("Keine Divera-Einstellungen für diese Einheit"));
    }

    /** Nur nicht geschlossene Einsätze (für Dashboard / später Push-Hook). */
    @Transactional(readOnly = true)
    public List<DiveraAlarmSummary> getOpenAlarmsForUnit(long unitId) {
        DiveraAlarmsResponse r = getAlarmsForUnit(unitId);
        if (!r.success()) {
            return List.of();
        }
        return r.alarms().stream().filter(a -> !a.closed()).toList();
    }
}
