package de.feuerwehr.manager.auswertung;

import de.feuerwehr.manager.atemschutz.AtemschutzService;
import de.feuerwehr.manager.berichte.AnwesenheitslisteService;
import de.feuerwehr.manager.berichte.AttendanceReport;
import de.feuerwehr.manager.berichte.AttendanceReportRepository;
import de.feuerwehr.manager.berichte.Besatzungsstaerke;
import de.feuerwehr.manager.berichte.IncidentReport;
import de.feuerwehr.manager.berichte.IncidentReportPersonnel;
import de.feuerwehr.manager.berichte.IncidentReportPersonnelRepository;
import de.feuerwehr.manager.berichte.IncidentReportRepository;
import de.feuerwehr.manager.berichte.IncidentReportStatus;
import de.feuerwehr.manager.berichte.IncidentReportVehicle;
import de.feuerwehr.manager.berichte.IncidentReportVehicleRepository;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.termine.TermineCategory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuswertungService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final IncidentReportRepository incidentReportRepository;
    private final IncidentReportPersonnelRepository incidentReportPersonnelRepository;
    private final IncidentReportVehicleRepository incidentReportVehicleRepository;
    private final AttendanceReportRepository attendanceReportRepository;
    private final AnwesenheitslisteService anwesenheitslisteService;
    private final PersonalService personalService;
    private final PersonRepository personRepository;
    private final AtemschutzService atemschutzService;
    private final ModuleSettingsService moduleSettingsService;
    private final TestModeService testModeService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AuswertungOverviewStats overviewStats(long unitId, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEndExclusive = LocalDate.of(year + 1, 1, 1);
        LocalDate today = LocalDate.now();
        boolean includeTest = testModeService.isEnabled();

        int feuer = 0;
        int th = 0;
        int cbrn = 0;
        int sonstiges = 0;
        long feuerMin = 0;
        long thMin = 0;
        long cbrnMin = 0;
        long sonstigesMin = 0;
        long einsaetzeMin = 0;

        List<IncidentReport> reports = listFreigegebeneEinsaetze(unitId, yearStart, yearEndExclusive, includeTest);
        for (IncidentReport report : reports) {
            long minutes = durationMinutes(report.getAlarmTime(), report.getEndTime());
            einsaetzeMin += minutes;
            switch (AuswertungStichwortKategorie.classify(report.getStichwort())) {
                case FEUER -> {
                    feuer++;
                    feuerMin += minutes;
                }
                case TH -> {
                    th++;
                    thMin += minutes;
                }
                case CBRN -> {
                    cbrn++;
                    cbrnMin += minutes;
                }
                case SONSTIGES -> {
                    sonstiges++;
                    sonstigesMin += minutes;
                }
            }
        }
        int einsaetze = reports.size();

        int uebungsdienste = 0;
        long uebungMin = 0;
        LocalDateRange uebungRange = uebungDateRange(yearStart, yearEndExclusive, today);
        if (uebungRange != null) {
            List<AttendanceReport> uebungen =
                    listFreigegebeneUebungsdienste(unitId, uebungRange.from(), uebungRange.to(), includeTest);
            uebungsdienste = uebungen.size();
            for (AttendanceReport report : uebungen) {
                uebungMin += durationMinutes(report.getStartTime(), report.getEndTime());
            }
        }

        int mitglieder = personalService.listPersons(unitId).size();
        int tauglichePa = countTauglichePaTraeger(unitId);

        return new AuswertungOverviewStats(
                einsaetze,
                formatStundenTotal(einsaetzeMin),
                feuer,
                formatStundenTotal(feuerMin),
                th,
                formatStundenTotal(thMin),
                cbrn,
                formatStundenTotal(cbrnMin),
                sonstiges,
                formatStundenTotal(sonstigesMin),
                uebungsdienste,
                formatStundenTotal(uebungMin),
                mitglieder,
                tauglichePa);
    }

    @Transactional(readOnly = true)
    public List<AuswertungEinsatzRow> listDetailRows(
            long unitId, int year, AuswertungOverviewDetail detail) {
        if (detail == null) {
            return List.of();
        }
        if (detail == AuswertungOverviewDetail.UEBUNGSDIENSTE) {
            return listUebungsdienstRows(unitId, year, detail);
        }
        return listEinsatzRows(unitId, year, detail);
    }

    /**
     * Personen-Auswertung: Dienstbeteiligung = Anteil an freigegebenen Übungsdienst-Anwesenheitslisten
     * (bis heute), Einsatzbeteiligung = Anteil an freigegebenen Einsatzberichten im Jahr.
     * Entwürfe zählen nicht.
     */
    @Transactional(readOnly = true)
    public List<AuswertungPersonRow> listPersonRows(long unitId, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEndExclusive = LocalDate.of(year + 1, 1, 1);
        boolean includeTest = testModeService.isEnabled();

        List<Person> persons = personalService.listPersons(unitId).stream()
                .sorted(Comparator.comparing(
                                (Person p) -> p.getLastName() != null ? p.getLastName() : "",
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(
                                p -> p.getFirstName() != null ? p.getFirstName() : "",
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (persons.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> dienstCountByPerson = new HashMap<>();
        Map<Long, Integer> einsatzCountByPerson = new HashMap<>();
        Map<Long, List<DatedTeilnahme>> diensteByPerson = new HashMap<>();
        Map<Long, List<DatedTeilnahme>> einsaetzeByPerson = new HashMap<>();

        int totalUebungen = 0;
        LocalDateRange uebungRange = uebungDateRange(yearStart, yearEndExclusive, LocalDate.now());
        if (uebungRange != null) {
            List<AttendanceReport> uebungen =
                    listFreigegebeneUebungsdienste(unitId, uebungRange.from(), uebungRange.to(), includeTest);
            totalUebungen = uebungen.size();
            for (AttendanceReport report : uebungen) {
                LocalDate eventDate = report.getEventDate();
                String label = report.getTitle() != null && !report.getTitle().isBlank()
                        ? report.getTitle().trim()
                        : "Übungsdienst";
                AnwesenheitslisteService.AnwesenheitPersonIds ids =
                        anwesenheitslisteService.presentAndPaPersonIds(unitId, report.getId());
                for (Long personId : ids.presentIds()) {
                    boolean pa = ids.paIds().contains(personId);
                    dienstCountByPerson.merge(personId, 1, Integer::sum);
                    diensteByPerson
                            .computeIfAbsent(personId, id -> new ArrayList<>())
                            .add(new DatedTeilnahme(eventDate, label, pa));
                }
            }
        }

        List<IncidentReport> einsaetze =
                listFreigegebeneEinsaetze(unitId, yearStart, yearEndExclusive, includeTest);
        int totalEinsaetze = einsaetze.size();
        if (!einsaetze.isEmpty()) {
            Map<Long, IncidentReport> einsatzById = new HashMap<>();
            for (IncidentReport report : einsaetze) {
                einsatzById.put(report.getId(), report);
            }
            Map<Long, Map<Long, DatedTeilnahme>> einsatzTeilnahmeByPerson = new HashMap<>();
            List<Long> reportIds = einsaetze.stream().map(IncidentReport::getId).toList();
            for (IncidentReportPersonnel row :
                    incidentReportPersonnelRepository.findByIncidentReportIdIn(reportIds)) {
                if (row.getPerson() == null || row.getIncidentReport() == null) {
                    continue;
                }
                long personId = row.getPerson().getId();
                long reportId = row.getIncidentReport().getId();
                IncidentReport report = einsatzById.get(reportId);
                if (report == null) {
                    continue;
                }
                String label = report.getStichwort() != null && !report.getStichwort().isBlank()
                        ? report.getStichwort().trim()
                        : "Einsatz";
                Map<Long, DatedTeilnahme> byReport =
                        einsatzTeilnahmeByPerson.computeIfAbsent(personId, id -> new HashMap<>());
                DatedTeilnahme existing = byReport.get(reportId);
                boolean pa = row.isUsesPa() || (existing != null && existing.pa());
                if (existing == null) {
                    einsatzCountByPerson.merge(personId, 1, Integer::sum);
                }
                byReport.put(reportId, new DatedTeilnahme(report.getIncidentDate(), label, pa));
            }
            for (Map.Entry<Long, Map<Long, DatedTeilnahme>> entry : einsatzTeilnahmeByPerson.entrySet()) {
                einsaetzeByPerson.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
            }
        }

        List<AuswertungPersonRow> rows = new ArrayList<>(persons.size());
        for (Person person : persons) {
            int dienst = dienstCountByPerson.getOrDefault(person.getId(), 0);
            int einsatz = einsatzCountByPerson.getOrDefault(person.getId(), 0);
            double dienstPct = totalUebungen > 0 ? (dienst * 100.0) / totalUebungen : 0;
            double einsatzPct = totalEinsaetze > 0 ? (einsatz * 100.0) / totalEinsaetze : 0;
            rows.add(new AuswertungPersonRow(
                    person.getId(),
                    person.anwesenheitDisplayName(),
                    formatBeteiligungPct(dienst, totalUebungen),
                    formatBeteiligungPct(einsatz, totalEinsaetze),
                    dienstPct,
                    einsatzPct,
                    formatBeteiligungQuote(dienst, totalUebungen),
                    formatBeteiligungQuote(einsatz, totalEinsaetze),
                    toTeilnahmeList(diensteByPerson.get(person.getId())),
                    toTeilnahmeList(einsaetzeByPerson.get(person.getId()))));
        }
        return rows;
    }

    private static List<AuswertungPersonTeilnahme> toTeilnahmeList(List<DatedTeilnahme> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .sorted(Comparator.comparing(
                                DatedTeilnahme::date, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .map(t -> new AuswertungPersonTeilnahme(
                        t.date() != null ? t.date().format(DATE_FMT) : "—",
                        t.label(),
                        t.pa()))
                .toList();
    }

    private record DatedTeilnahme(LocalDate date, String label, boolean pa) {}

    private static String formatBeteiligungPct(int attended, int total) {
        if (total <= 0) {
            return "—";
        }
        double pct = attended * 100.0 / total;
        if (Math.abs(pct - Math.rint(pct)) < 0.05) {
            return String.format(Locale.GERMAN, "%.0f %%", pct);
        }
        return String.format(Locale.GERMAN, "%.1f %%", pct);
    }

    private static String formatBeteiligungQuote(int attended, int total) {
        if (total <= 0) {
            return "—";
        }
        return attended + "/" + total;
    }

    private List<AuswertungEinsatzRow> listEinsatzRows(
            long unitId, int year, AuswertungOverviewDetail detail) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEndExclusive = LocalDate.of(year + 1, 1, 1);
        boolean includeTest = testModeService.isEnabled();
        String returnUrl = buildReturnUrl(unitId, year, detail);

        List<IncidentReport> reports = listFreigegebeneEinsaetze(unitId, yearStart, yearEndExclusive, includeTest)
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
        Map<Long, List<IncidentReportVehicle>> vehiclesByReport = new HashMap<>();
        for (IncidentReportVehicle row :
                incidentReportVehicleRepository.findByIncidentReportIdIn(reportIds)) {
            vehiclesByReport
                    .computeIfAbsent(row.getIncidentReport().getId(), id -> new ArrayList<>())
                    .add(row);
        }

        List<AuswertungEinsatzRow> rows = new ArrayList<>(reports.size());
        for (IncidentReport report : reports) {
            List<IncidentReportPersonnel> crew =
                    personnelByReport.getOrDefault(report.getId(), List.of());
            List<String> personen = crew.stream()
                    .map(this::personnelDisplayName)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            List<String> paTraeger = crew.stream()
                    .filter(IncidentReportPersonnel::isUsesPa)
                    .map(this::personnelDisplayName)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            List<String> fahrzeuge = vehiclesByReport.getOrDefault(report.getId(), List.of()).stream()
                    .filter(IncidentReportVehicle::isInvolved)
                    .map(IncidentReportVehicle::getVehicleName)
                    .filter(name -> name != null && !name.isBlank())
                    .map(String::trim)
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            if (fahrzeuge.isEmpty()) {
                fahrzeuge = vehiclesByReport.getOrDefault(report.getId(), List.of()).stream()
                        .map(IncidentReportVehicle::getVehicleName)
                        .filter(name -> name != null && !name.isBlank())
                        .map(String::trim)
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
            }

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
                    report.getId(),
                    "einsatz",
                    report.getIncidentDate(),
                    blankToDash(report.getStichwort()),
                    formatDauerStunden(report.getAlarmTime(), report.getEndTime()),
                    personen.size(),
                    zf,
                    gf,
                    formatTime(report.getAlarmTime()),
                    formatTime(report.getEndTime()),
                    "Einsatzleiter",
                    resolveEinsatzleiter(report),
                    personen,
                    paTraeger,
                    fahrzeuge,
                    buildViewUrl("/berichte/einsatzberichte/" + report.getId(), unitId, returnUrl),
                    "Zum Einsatzbericht"));
        }
        return rows;
    }

    private List<AuswertungEinsatzRow> listUebungsdienstRows(
            long unitId, int year, AuswertungOverviewDetail detail) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEndExclusive = LocalDate.of(year + 1, 1, 1);
        LocalDateRange range = uebungDateRange(yearStart, yearEndExclusive, LocalDate.now());
        if (range == null) {
            return List.of();
        }
        boolean includeTest = testModeService.isEnabled();
        String returnUrl = buildReturnUrl(unitId, year, detail);

        List<AttendanceReport> reports =
                listFreigegebeneUebungsdienste(unitId, range.from(), range.to(), includeTest);
        if (reports.isEmpty()) {
            return List.of();
        }

        List<AuswertungEinsatzRow> rows = new ArrayList<>(reports.size());
        for (AttendanceReport report : reports) {
            AnwesenheitslisteService.AnwesenheitPresenceSummary presence =
                    anwesenheitslisteService.presenceSummary(unitId, report.getId());
            rows.add(new AuswertungEinsatzRow(
                    report.getId(),
                    "uebung",
                    report.getEventDate(),
                    blankToDash(report.getTitle()),
                    formatDauerStunden(report.getStartTime(), report.getEndTime()),
                    presence.personal(),
                    presence.zf(),
                    presence.gf(),
                    formatTime(report.getStartTime()),
                    formatTime(report.getEndTime()),
                    "Ausbilder",
                    resolveAusbilder(report),
                    presence.personen(),
                    presence.paTraeger(),
                    presence.fahrzeuge(),
                    buildViewUrl("/berichte/anwesenheitslisten/" + report.getId(), unitId, returnUrl),
                    "Zur Anwesenheitsliste"));
        }
        return rows;
    }

    private static String resolveEinsatzleiter(IncidentReport report) {
        if (report.getCommanderPerson() != null) {
            String name = report.getCommanderPerson().displayName();
            if (name != null && !name.isBlank()) {
                return name.trim();
            }
        }
        if (report.getIncidentCommander() != null && !report.getIncidentCommander().isBlank()) {
            return report.getIncidentCommander().trim();
        }
        return "—";
    }

    private String resolveAusbilder(AttendanceReport report) {
        if (report.getInstructorResponsible() != null && !report.getInstructorResponsible().isBlank()) {
            return report.getInstructorResponsible().trim();
        }
        List<Long> ids = parseInstructorPersonIds(report.getInstructorPersonIdsJson());
        if (ids.isEmpty()) {
            return "—";
        }
        Map<Long, Person> byId = new LinkedHashMap<>();
        personRepository.findAllById(ids).forEach(person -> byId.put(person.getId(), person));
        List<String> names = new ArrayList<>();
        for (Long id : ids) {
            Person person = byId.get(id);
            if (person != null) {
                names.add(person.anwesenheitDisplayName());
            }
        }
        if (names.isEmpty()) {
            return "—";
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", names);
    }

    private List<Long> parseInstructorPersonIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Long> ids = objectMapper.readValue(json, new TypeReference<>() {});
            return ids != null
                    ? ids.stream().filter(Objects::nonNull).distinct().toList()
                    : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String personnelDisplayName(IncidentReportPersonnel row) {
        if (row.getDisplayName() != null && !row.getDisplayName().isBlank()) {
            return row.getDisplayName().trim();
        }
        if (row.getPerson() != null) {
            return row.getPerson().displayName();
        }
        return "Unbekannt";
    }

    private static String buildReturnUrl(long unitId, int year, AuswertungOverviewDetail detail) {
        return "/auswertung?unit=" + unitId + "&jahr=" + year + "&detail=" + detail.key();
    }

    private static String buildViewUrl(String path, long unitId, String returnUrl) {
        return path
                + "?unit="
                + unitId
                + "&returnUrl="
                + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
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
        long minutes = durationMinutes(from, to);
        if (from == null || to == null) {
            return "—";
        }
        return formatStundenValue(minutes);
    }

    static String formatStundenTotal(long minutes) {
        if (minutes <= 0) {
            return "0 Std.";
        }
        return formatStundenValue(minutes) + " Std.";
    }

    private static String formatStundenValue(long minutes) {
        double hours = minutes / 60.0;
        if (Math.abs(hours - Math.rint(hours)) < 0.0001) {
            return String.format(Locale.GERMAN, "%.0f", hours);
        }
        return String.format(Locale.GERMAN, "%.1f", hours);
    }

    private static long durationMinutes(LocalTime from, LocalTime to) {
        if (from == null || to == null) {
            return 0;
        }
        long minutes = Duration.between(from, to).toMinutes();
        if (minutes < 0) {
            minutes += 24 * 60;
        }
        return minutes;
    }

    private static String formatTime(LocalTime time) {
        return time != null ? time.format(TIME_FMT) : "—";
    }

    private static String blankToDash(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        return value.trim();
    }

    /** Freigegebene und archivierte Einsatzberichte (keine Entwürfe). */
    private List<IncidentReport> listFreigegebeneEinsaetze(
            long unitId, LocalDate yearStart, LocalDate yearEndExclusive, boolean includeTest) {
        return incidentReportRepository.findByUnitIdAndYear(unitId, yearStart, yearEndExclusive, includeTest)
                .stream()
                .filter(r -> isFreigegeben(r.getStatus()))
                .toList();
    }

    /** Freigegebene Übungsdienst-Anwesenheitslisten (Kategorie DIENSTPLAN, keine Entwürfe). */
    private List<AttendanceReport> listFreigegebeneUebungsdienste(
            long unitId, LocalDate from, LocalDate to, boolean includeTest) {
        return attendanceReportRepository.findByUnitIdAndDateRange(unitId, from, to, includeTest).stream()
                .filter(r -> r.getTerminCategory() == TermineCategory.DIENSTPLAN)
                .filter(r -> isFreigegeben(r.getStatus()))
                .toList();
    }

    private static boolean isFreigegeben(IncidentReportStatus status) {
        return status == IncidentReportStatus.FREIGEGEBEN
                || status == IncidentReportStatus.ARCHIVIERT;
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
