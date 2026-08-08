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
import de.feuerwehr.manager.util.PersonMembership;
import de.feuerwehr.manager.util.YearFilterSupport;
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
import java.util.Optional;
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

        int mitglieder = (int) personalService.listPersons(unitId).stream()
                .filter(de.feuerwehr.manager.util.PersonMembership::isCurrentlyMember)
                .count();
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
     * Jahre mit freigegebenen/archivierten Einsätzen oder Übungsdiensten (absteigend).
     */
    @Transactional(readOnly = true)
    public List<Integer> availableYears(long unitId) {
        boolean includeTest = testModeService.isEnabled();
        List<IncidentReportStatus> statuses =
                List.of(IncidentReportStatus.FREIGEGEBEN, IncidentReportStatus.ARCHIVIERT);
        return YearFilterSupport.mergeDescending(
                incidentReportRepository.findDistinctYearsByUnitIdAndStatuses(unitId, statuses, includeTest),
                attendanceReportRepository.findDistinctYearsByUnitIdCategoryAndStatuses(
                        unitId, TermineCategory.DIENSTPLAN, statuses, includeTest));
    }

    /**
     * Personen-Auswertung: Dienstbeteiligung = Anteil an freigegebenen Übungsdienst-Anwesenheitslisten
     * (bis heute), Einsatzbeteiligung = Anteil an freigegebenen Einsatzberichten im Jahr.
     * Entwürfe zählen nicht. Eintrittsdatum: nur Termine ab Eintritt; ohne Eintrittsdatum alle Termine.
     */
    @Transactional(readOnly = true)
    public List<AuswertungPersonRow> listPersonRows(long unitId, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEndExclusive = LocalDate.of(year + 1, 1, 1);
        boolean includeTest = testModeService.isEnabled();

        List<Person> persons = personalService.listPersons(unitId).stream()
                .filter(p -> PersonMembership.wasMemberDuringYear(p, year))
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

        ParticipationMaps maps = buildParticipationMaps(unitId, yearStart, yearEndExclusive, includeTest);

        List<AuswertungPersonRow> rows = new ArrayList<>(persons.size());
        for (Person person : persons) {
            rows.add(toPersonRow(person, maps));
        }
        return rows;
    }

    /**
     * Persönliche Beteiligung für die Startseite (gleiche Zählregeln wie {@link #listPersonRows}).
     */
    @Transactional(readOnly = true)
    public Optional<DashboardParticipationStats> participationStatsForPerson(
            long unitId, long personId, int year) {
        boolean includeTest = testModeService.isEnabled();
        Person person = personRepository.findActiveById(personId, includeTest).orElse(null);
        if (person == null
                || person.getUnit() == null
                || person.getUnit().getId() != unitId
                || !PersonMembership.wasMemberDuringYear(person, year)) {
            return Optional.empty();
        }
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEndExclusive = LocalDate.of(year + 1, 1, 1);
        ParticipationMaps maps = buildParticipationMaps(unitId, yearStart, yearEndExclusive, includeTest);
        LocalDate entryDate = person.getEntryDate();
        LocalDate exitDate = person.getExitDate();
        int totalUebungen =
                countEventsInMembership(maps.uebungen(), AttendanceReport::getEventDate, entryDate, exitDate);
        int totalEinsaetze =
                countEventsInMembership(maps.einsaetze(), IncidentReport::getIncidentDate, entryDate, exitDate);
        int attendedUebungen =
                filterTeilnahmen(maps.diensteByPerson().get(person.getId()), entryDate, exitDate).size();
        int attendedEinsaetze =
                filterTeilnahmen(maps.einsaetzeByPerson().get(person.getId()), entryDate, exitDate).size();
        double uebungPct = totalUebungen > 0 ? (attendedUebungen * 100.0) / totalUebungen : 0;
        double einsatzPct = totalEinsaetze > 0 ? (attendedEinsaetze * 100.0) / totalEinsaetze : 0;
        return Optional.of(new DashboardParticipationStats(
                year,
                person.anwesenheitDisplayName(),
                attendedUebungen,
                totalUebungen,
                formatBeteiligungQuote(attendedUebungen, totalUebungen),
                formatBeteiligungPct(attendedUebungen, totalUebungen),
                uebungPct,
                attendedEinsaetze,
                totalEinsaetze,
                formatBeteiligungQuote(attendedEinsaetze, totalEinsaetze),
                formatBeteiligungPct(attendedEinsaetze, totalEinsaetze),
                einsatzPct));
    }

    private ParticipationMaps buildParticipationMaps(
            long unitId, LocalDate yearStart, LocalDate yearEndExclusive, boolean includeTest) {
        List<AttendanceReport> uebungen = List.of();
        LocalDateRange uebungRange = uebungDateRange(yearStart, yearEndExclusive, LocalDate.now());
        if (uebungRange != null) {
            uebungen = listFreigegebeneUebungsdienste(unitId, uebungRange.from(), uebungRange.to(), includeTest);
        }

        Map<Long, List<DatedTeilnahme>> diensteByPerson = new HashMap<>();
        for (AttendanceReport report : uebungen) {
            LocalDate eventDate = report.getEventDate();
            String label = report.getTitle() != null && !report.getTitle().isBlank()
                    ? report.getTitle().trim()
                    : "Übungsdienst";
            AnwesenheitslisteService.AnwesenheitPersonIds ids =
                    anwesenheitslisteService.presentAndPaPersonIds(unitId, report.getId());
            for (Long pid : ids.presentIds()) {
                boolean pa = ids.paIds().contains(pid);
                diensteByPerson
                        .computeIfAbsent(pid, id -> new ArrayList<>())
                        .add(new DatedTeilnahme(eventDate, label, pa));
            }
        }

        List<IncidentReport> einsaetze =
                listFreigegebeneEinsaetze(unitId, yearStart, yearEndExclusive, includeTest);
        Map<Long, List<DatedTeilnahme>> einsaetzeByPerson = new HashMap<>();
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
                long pid = row.getPerson().getId();
                long reportId = row.getIncidentReport().getId();
                IncidentReport report = einsatzById.get(reportId);
                if (report == null) {
                    continue;
                }
                String label = report.getStichwort() != null && !report.getStichwort().isBlank()
                        ? report.getStichwort().trim()
                        : "Einsatz";
                Map<Long, DatedTeilnahme> byReport =
                        einsatzTeilnahmeByPerson.computeIfAbsent(pid, id -> new HashMap<>());
                DatedTeilnahme existing = byReport.get(reportId);
                boolean pa = row.isUsesPa() || (existing != null && existing.pa());
                byReport.put(reportId, new DatedTeilnahme(report.getIncidentDate(), label, pa));
            }
            for (Map.Entry<Long, Map<Long, DatedTeilnahme>> entry : einsatzTeilnahmeByPerson.entrySet()) {
                einsaetzeByPerson.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
            }
        }
        return new ParticipationMaps(uebungen, einsaetze, diensteByPerson, einsaetzeByPerson);
    }

    private AuswertungPersonRow toPersonRow(Person person, ParticipationMaps maps) {
        LocalDate entryDate = person.getEntryDate();
        LocalDate exitDate = person.getExitDate();
        int totalUebungen =
                countEventsInMembership(maps.uebungen(), AttendanceReport::getEventDate, entryDate, exitDate);
        int totalEinsaetze =
                countEventsInMembership(maps.einsaetze(), IncidentReport::getIncidentDate, entryDate, exitDate);

        List<DatedTeilnahme> dienste =
                filterTeilnahmen(maps.diensteByPerson().get(person.getId()), entryDate, exitDate);
        List<DatedTeilnahme> einsatzTeilnahmen =
                filterTeilnahmen(maps.einsaetzeByPerson().get(person.getId()), entryDate, exitDate);
        int dienst = dienste.size();
        int einsatz = einsatzTeilnahmen.size();
        double dienstPct = totalUebungen > 0 ? (dienst * 100.0) / totalUebungen : 0;
        double einsatzPct = totalEinsaetze > 0 ? (einsatz * 100.0) / totalEinsaetze : 0;
        return new AuswertungPersonRow(
                person.getId(),
                person.anwesenheitDisplayName(),
                formatBeteiligungPct(dienst, totalUebungen),
                formatBeteiligungPct(einsatz, totalEinsaetze),
                dienstPct,
                einsatzPct,
                formatBeteiligungQuote(dienst, totalUebungen),
                formatBeteiligungQuote(einsatz, totalEinsaetze),
                toTeilnahmeList(dienste),
                toTeilnahmeList(einsatzTeilnahmen));
    }

    private record ParticipationMaps(
            List<AttendanceReport> uebungen,
            List<IncidentReport> einsaetze,
            Map<Long, List<DatedTeilnahme>> diensteByPerson,
            Map<Long, List<DatedTeilnahme>> einsaetzeByPerson) {}

    private static <T> int countEventsInMembership(
            List<T> events,
            java.util.function.Function<T, LocalDate> dateGetter,
            LocalDate entryDate,
            LocalDate exitDate) {
        if (events == null || events.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (T event : events) {
            if (YearFilterSupport.isWithinMembership(dateGetter.apply(event), entryDate, exitDate)) {
                count++;
            }
        }
        return count;
    }

    private static List<DatedTeilnahme> filterTeilnahmen(
            List<DatedTeilnahme> items, LocalDate entryDate, LocalDate exitDate) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (entryDate == null && exitDate == null) {
            return items;
        }
        List<DatedTeilnahme> filtered = new ArrayList<>();
        for (DatedTeilnahme item : items) {
            if (YearFilterSupport.isWithinMembership(item.date(), entryDate, exitDate)) {
                filtered.add(item);
            }
        }
        return filtered;
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
