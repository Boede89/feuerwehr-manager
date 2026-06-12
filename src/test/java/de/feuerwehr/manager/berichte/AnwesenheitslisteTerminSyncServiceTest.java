package de.feuerwehr.manager.berichte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.termine.TermineCategory;
import de.feuerwehr.manager.termine.UnitTermin;
import de.feuerwehr.manager.termine.UnitTerminRepository;
import de.feuerwehr.manager.unit.Unit;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnwesenheitslisteTerminSyncServiceTest {

    @Mock
    private UnitTerminRepository unitTerminRepository;

    @Mock
    private AnwesenheitslisteService anwesenheitslisteService;

    @Mock
    private ModuleSettingsService moduleSettingsService;

    @InjectMocks
    private AnwesenheitslisteTerminSyncService syncService;

    @Test
    void syncTerminsForUnitCreatesDraftsForDienstplanAndSonstiges() {
        when(moduleSettingsService.isEnabled(AppModule.BERICHTE, 1L)).thenReturn(true);
        UnitTermin termin = termin(TermineCategory.DIENSTPLAN, "Übung");
        when(unitTerminRepository.findByUnitIdAndCategoryIn(eq(1L), any())).thenReturn(List.of(termin));
        when(anwesenheitslisteService.createDraftFromTerminIfMissing(1L, termin)).thenReturn(true);

        AnwesenheitslisteTerminSyncService.SyncResult result = syncService.syncTerminsForUnit(1L);

        assertThat(result.success()).isTrue();
        assertThat(result.created()).isEqualTo(1);
        verify(anwesenheitslisteService).createDraftFromTerminIfMissing(1L, termin);
    }

    @Test
    void syncSingleTerminSkipsFahrzeuge() {
        when(moduleSettingsService.isEnabled(AppModule.BERICHTE, 1L)).thenReturn(true);
        UnitTermin termin = termin(TermineCategory.FAHRZEUGE, "Fahrzeugcheck");

        syncService.syncSingleTermin(1L, termin);

        verify(anwesenheitslisteService, never()).createDraftFromTerminIfMissing(eq(1L), any());
    }

    private static UnitTermin termin(TermineCategory category, String title) {
        Unit unit = new Unit();
        unit.setId(1L);
        UnitTermin termin = new UnitTermin();
        termin.setId(10L);
        termin.setUnit(unit);
        termin.setCategory(category);
        termin.setTitle(title);
        termin.setStartAt(LocalDateTime.of(2026, 6, 2, 19, 0));
        termin.setEndAt(LocalDateTime.of(2026, 6, 2, 21, 0));
        return termin;
    }
}
