package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.termine.TermineCategory;
import de.feuerwehr.manager.termine.UnitTermin;
import de.feuerwehr.manager.termine.UnitTerminRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnwesenheitslisteTerminSyncService {

    private static final List<TermineCategory> SYNC_CATEGORIES =
            List.of(TermineCategory.DIENSTPLAN, TermineCategory.SONDERDIENST, TermineCategory.SONSTIGES);

    private final UnitTerminRepository unitTerminRepository;
    private final AnwesenheitslisteService anwesenheitslisteService;
    private final ModuleSettingsService moduleSettingsService;

    public record SyncResult(boolean success, int created, int updated, String message) {}

    @Transactional
    public SyncResult syncTerminsForUnit(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.BERICHTE, unitId)) {
            return new SyncResult(false, 0, 0, "Modul Berichte ist deaktiviert.");
        }
        List<UnitTermin> termins = unitTerminRepository.findByUnitIdAndCategoryIn(unitId, SYNC_CATEGORIES);
        int created = 0;
        int updated = 0;
        for (UnitTermin termin : termins) {
            if (anwesenheitslisteService.createDraftFromTerminIfMissing(unitId, termin)) {
                created++;
            } else {
                anwesenheitslisteService.refreshDraftFromTermin(unitId, termin);
                updated++;
            }
        }
        String message = created == 0
                ? "Keine neuen Anwesenheitslisten aus Terminen übernommen."
                : created + " Anwesenheitsliste(n) aus Terminen als Entwurf angelegt.";
        log.info("[Termine→Anwesenheit] unit={} created={} refreshed={}", unitId, created, updated);
        return new SyncResult(true, created, updated, message);
    }

    @Transactional
    public void syncSingleTermin(long unitId, UnitTermin termin) {
        if (!moduleSettingsService.isEnabled(AppModule.BERICHTE, unitId)) {
            return;
        }
        if (termin == null || termin.getCategory() == null) {
            return;
        }
        if (!termin.getCategory().supportsAttendanceReports()) {
            return;
        }
        if (!anwesenheitslisteService.createDraftFromTerminIfMissing(unitId, termin)) {
            anwesenheitslisteService.refreshDraftFromTermin(unitId, termin);
        }
    }

    @Transactional
    public void onTerminDeleted(long unitId, long terminId, boolean deleteAttendanceReport) {
        if (deleteAttendanceReport) {
            anwesenheitslisteService.deleteForTermin(unitId, terminId);
        }
    }
}
