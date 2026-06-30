package de.feuerwehr.manager.divera;

import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.UnitDiveraSettings;
import de.feuerwehr.manager.unit.UnitDiveraSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Im Testmodus: aktuelle DIVERA-Einsätze periodisch als Beispiel-Payloads speichern
 * (auch ohne geöffneten Testalarm-Reiter).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiveraAlarmSampleScheduler {

    private final TestModeService testModeService;
    private final UnitDiveraSettingsRepository diveraSettingsRepository;
    private final DiveraService diveraService;

    @Scheduled(
            fixedDelayString = "${feuerwehr.divera.sample-sync-interval-ms:300000}",
            initialDelayString = "${feuerwehr.divera.sample-sync-initial-delay-ms:45000}")
    public void captureSamplesFromDiveraApi() {
        if (!testModeService.isEnabled()) {
            return;
        }
        for (UnitDiveraSettings cfg : diveraSettingsRepository.findAll()) {
            if (cfg.getUnit() == null) {
                continue;
            }
            long unitId = cfg.getUnit().getId();
            try {
                diveraService.syncAlarmSamplesForUnit(unitId);
            } catch (Exception e) {
                log.debug("[Divera-Beispiel] API-Sync unit={} fehlgeschlagen: {}", unitId, e.getMessage());
            }
        }
    }
}
