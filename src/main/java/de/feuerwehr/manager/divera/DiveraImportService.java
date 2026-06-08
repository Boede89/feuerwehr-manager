package de.feuerwehr.manager.divera;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Manueller DIVERA-Abruf und Anlage fehlender Einsatzbericht-Entwürfe. */
@Service
@RequiredArgsConstructor
public class DiveraImportService {

    private final DiveraEinsatzberichtSyncService einsatzberichtSyncService;

    public DiveraImportResult importAlarmsForUnit(long unitId) {
        DiveraEinsatzberichtSyncService.SyncResult sync = einsatzberichtSyncService.syncAlarmsForUnit(unitId);
        if (!sync.success()) {
            return DiveraImportResult.fail(sync.message());
        }
        return DiveraImportResult.ok(sync.created(), sync.skipped(), sync.message());
    }
}
