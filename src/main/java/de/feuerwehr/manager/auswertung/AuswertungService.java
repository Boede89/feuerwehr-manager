package de.feuerwehr.manager.auswertung;

import de.feuerwehr.manager.atemschutz.AtemschutzService;
import de.feuerwehr.manager.berichte.AttendancePersonStatus;
import de.feuerwehr.manager.berichte.AttendanceReport;
import de.feuerwehr.manager.berichte.AttendanceReportPersonnel;
import de.feuerwehr.manager.berichte.AttendanceReportPersonnelRepository;
import de.feuerwehr.manager.berichte.AttendanceReportRepository;
import de.feuerwehr.manager.berichte.Besatzungsstaerke;
import de.feuerwehr.manager.berichte.IncidentReport;
import de.feuerwehr.manager.berichte.IncidentReportPersonnel;
import de.feuerwehr.manager.berichte.IncidentReportPersonnelRepository;
import de.feuerwehr.manager.berichte.IncidentReportRepository;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.termine.TermineCategory;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuswertungService {

    private final IncidentReportRepository incidentReportRepository;
    private final IncidentReportPersonnelRepository incidentReportPersonnelRepository;
    private final AttendanceReportRepository attendanceReportRepository;
    private final AttendanceReportPersonnelRepository attendanceReportPersonnelRepository;
    private final PersonalService personalService;
    private final AtemschutzService atemschutzService;
    private final ModuleSettingsService moduleSettingsService;
    private final TestModeService testModeService;

    @Transactional(readOnly = true)
    public AuswertungOverviewStats overviewStats(long unitId, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEndExclusive = LocalDate.of(year + 1, 1, 1);
        LocalDate today = LocalDate.now();
        boolean includeTest = testModeService.isEnabled();

        List<String> stichworte = incidentReportRepository.findStichworteByUnitIdAndYear(
                unitId, yearStart, yearEndExclusive, includeTest);
        int feuer = 0;
        int th = 0;
        int cbrn = 0;
        int sonstiges = 0;
        for (String stichwort : stichworte) {
            switch (AuswertungStichwortKategorie.classify(stichwort)) {
                case FEUER -> feuer++;
                case TH -> th++;
                case CBRN -> cbrn++;
                case SONSTIGES -> sonstiges++;
            }
        }
        int einsaetze = stichworte.size();

        int uebungsdienste = 0;
        LocalDateRange uebungRange = uebungDateRange(yearStart, yearEndExclusive, today);
        if (uebungRange != null) {
            uebungsdienste = (int) attendanceReportRepository.countByUnitIdAndDateRangeAndCategory(
                    unitId, uebungRange.from(), uebungRange.to(), TermineCategory.DIENSTPLAN, includeTest);
        }

        int mitglieder = personalService.listPersons(unitId).size();
        int tauglichePa = countTauglichePaTraeger(unitId);

        return new AuswertungOverviewStats(
                einsaetze, feuer, th, cbrn, sonstiges, uebungsdienste, mitglieder, tauglichePa);
    }

    @Transactional(readOnly = true)
    public List<AuswertungEinsatzRow> listDetailRows(long unitId, int year, AuswertungOverviewDetail detail) {
        if (detail == null) {
            return List.of();
        }
        if (detail == AuswertungOverviewDetail.UEBUNGSDIENSTE) {
            return listUebungsdienstRows(unitId, year);
        }
        return listEinsatzRows(unitId, year, detail);
    }

    private List<AuswertungEinsatzRow> listEinsatzRows(long unitId, int year, AuswertungOverviewDetail detail) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEndExclusive = LocalDate.of(year + 1, 1, 1);
        boolean includeTest = testModeService.isEnabled();

        List<IncidentReport> reports = incidentReportRepository
                .findByUnitIdAndYear(unitId, yearStart, yearEndExclusive, includeTest)
                .stream()
                .filter(r -> detail.matches(AuswertungStichwortKategorie.classify(r.getStichwort())))
                .toList();
        if (reports.isEmpty()) {
            return List.of();
        }

        List<Long> reportIds = reports.stream().map(IncidentReport::getId).toList();
        Map<Long, List<IncidentReportPersonnel>> personnelByReport = new HashMap<>();
        for (IncidentReportPersonnel row :
                incidentReportPersonnelRepository.findByIncidentReportIdIn(reportIds)) {
            personnelByReport
                    .computeIfAbsent(row.getIncidentReport().getId(), id -> new ArrayList<>())
                    .add(row);
        }

        List<AuswertungEinsatzRow> rows = new ArrayList<>(reports.size());
        for (IncidentReport report : reports) {
            List<IncidentReportPersonnel> crew =
                    personnelByReport.getOrDefault(report.getId(), List.of());
            int personal = crew.size();
            int zf = 0;
            int gf = 0;
            for (IncidentReportPersonnel member : crew) {
                switch (Besatzungsstaerke.qualTier(member.getPerson())) {
                    case ZF -> zf++;
                    case GF -> gf++;
                    default -> {
                        // Mannschaft
                    }
                }
            }
            rows.add(new AuswertungEinsatzRow(
                    report.getIncidentDate(),
                    blankToDash(report.getStichwort()),
                    formatDauerStunden(report.getAlarmTime(), report.getEndTime()),
                    personal,
                    zf,
                    gf));
        }
        return rows;
    }

    private List<AuswertungEinsatzRow> listUebungsdienstRows(long unitId, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEndExclusive = LocalDate.of(year + 1, 1, 1);
        LocalDateRange range = uebungDateRange(yearStart, yearEndExclusive, LocalDate.now());
        if (range == null) {
            return List.of();
        }
        boolean includeTest = testModeService.isEnabled();

        List<AttendanceReport> reports = attendanceReportRepository
                .findByUnitIdAndDateRange(unitId, range.from(), range.to(), includeTest)
                .stream()
                .filter(r -> r.getTerminCategory() == TermineCategory.DIENSTPLAN)
                .toList();
        if (reports.isEmpty()) {
            return List.of();
        }

        List<Long> reportIds = reports.stream().map(AttendanceReport::getId).toList();
        Map<Long, List<AttendanceReportPersonnel>> personnelByReport = new HashMap<>();
        for (AttendanceReportPersonnel row : attendanceReportPersonnelRepository.findByReportIdIn(reportIds)) {
            if (row.getAttendanceStatus() != AttendancePersonStatus.PRESENT) {
                continue;
            }
            personnelByReport
                    .computeIfAbsent(row.getAttendanceReport().getId(), id -> new ArrayList<>())
                    .add(row);
        }

        List<AuswertungEinsatzRow> rows = new ArrayList<>(reports.size());
        for (AttendanceReport report : reports) {
            List<AttendanceReportPersonnel> crew =
                    personnelByReport.getOrDefault(report.getId(), List.of());
            int personal = crew.size();
            int zf = 0;
            int gf = 0;
            for (AttendanceReportPersonnel member : crew) {
                switch (Besatzungsstaerke.qualTier(member.getPerson())) {
                    case ZF -> zf++;
                    case GF -> gf++;
                    default -> {
                        // Mannschaft
                    }
                }
            }
            rows.add(new AuswertungEinsatzRow(
                    report.getEventDate(),
                    blankToDash(report.getTitle()),
                    formatDauerStunden(report.getStartTime(), report.getEndTime()),
                    personal,
                    zf,
                    gf));
        }
        return rows;
    }

    private static LocalDateRange uebungDateRange(LocalDate yearStart, LocalDate yearEndExclusive, LocalDate today) {
        LocalDate to = today;
        if (to.isBefore(yearStart)) {
            return null;
        }
        if (to.isAfter(yearEndExclusive.minusDays(1))) {
            to = yearEndExclusive.minusDays(1);
        }
        return new LocalDateRange(yearStart, to);
    }

    static String formatDauerStunden(LocalTime from, LocalTime to) {
        if (from == null || to == null) {
            return "—";
        }
        long minutes = Duration.between(from, to).toMinutes();
        if (minutes < 0) {
            minutes += 24 * 60;
        }
        double hours = minutes / 60.0;
        if (Math.abs(hours - Math.rint(hours)) < 0.0001) {
            return String.format(Locale.GERMAN, "%.0f", hours);
        }
        return String.format(Locale.GERMAN, "%.1f", hours);
    }

    private static String blankToDash(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        return value.trim();
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

    private record LocalDateRange(LocalDate from, LocalDate to) {}
}
