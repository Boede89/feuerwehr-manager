package de.feuerwehr.manager.auswertung;

import de.feuerwehr.manager.atemschutz.AtemschutzService;
import de.feuerwehr.manager.berichte.AttendanceReportRepository;
import de.feuerwehr.manager.berichte.IncidentReportRepository;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.termine.TermineCategory;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuswertungService {

    private final IncidentReportRepository incidentReportRepository;
    private final AttendanceReportRepository attendanceReportRepository;
    private final PersonalService personalService;
    private final AtemschutzService atemschutzService;
    private final ModuleSettingsService moduleSettingsService;
    private final TestModeService testModeService;

    @Transactional
    public AuswertungOverviewStats overviewStats(long unitId, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEndExclusive = LocalDate.of(year + 1, 1, 1);
        LocalDate today = LocalDate.now();
        boolean includeTest = testModeService.isEnabled();

        int einsaetze = (int) incidentReportRepository.countByUnitIdAndYear(
                unitId, yearStart, yearEndExclusive, includeTest);

        LocalDate uebungTo = today;
        if (uebungTo.isBefore(yearStart)) {
            // gewähltes Jahr liegt in der Zukunft — keine Übungsdienste
            uebungTo = yearStart.minusDays(1);
        } else if (uebungTo.isAfter(yearEndExclusive.minusDays(1))) {
            uebungTo = yearEndExclusive.minusDays(1);
        }
        int uebungsdienste = 0;
        if (!uebungTo.isBefore(yearStart)) {
            uebungsdienste = (int) attendanceReportRepository.countByUnitIdAndDateRangeAndCategory(
                    unitId, yearStart, uebungTo, TermineCategory.DIENSTPLAN, includeTest);
        }

        int mitglieder = personalService.listPersons(unitId).size();
        int tauglichePa = countTauglichePaTraeger(unitId);

        // Feuer / TH / CBRN / Sonstiges folgen später.
        return new AuswertungOverviewStats(einsaetze, 0, 0, 0, 0, uebungsdienste, mitglieder, tauglichePa);
    }

    private int countTauglichePaTraeger(long unitId) {
        try {
            if (!moduleSettingsService.isEnabled(AppModule.ATEMSCHUTZ, unitId)) {
                return 0;
            }
            return atemschutzService.listCarrierOverviews(unitId, "all").stats().tauglich();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
