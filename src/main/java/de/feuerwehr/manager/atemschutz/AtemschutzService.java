package de.feuerwehr.manager.atemschutz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.berichte.AttendanceReport;
import de.feuerwehr.manager.berichte.AttendanceReportRepository;
import de.feuerwehr.manager.berichte.IncidentReport;
import de.feuerwehr.manager.berichte.IncidentReportPersonnelRepository;
import de.feuerwehr.manager.berichte.IncidentReportStatus;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonCourseCompletionRepository;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.settings.TestModeDataMerge;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import de.feuerwehr.manager.util.PersonMembership;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AtemschutzService {

    public static final String FITNESS_SOURCE_INCIDENT_REPORT = "INCIDENT_REPORT";
    public static final String FITNESS_SOURCE_ATTENDANCE_REPORT = "ATTENDANCE_REPORT";

    private final UnitRepository unitRepository;
    private final PersonalService personalService;
    private final AtemschutzCarrierRepository carrierRepository;
    private final AtemschutzFitnessRecordRepository fitnessRecordRepository;
    private final PersonCourseCompletionRepository completionRepository;
    private final AtemschutzSettingsService atemschutzSettingsService;
    private final UserRepository userRepository;
    private final TestModeService testModeService;
    private final IncidentReportPersonnelRepository incidentReportPersonnelRepository;
    private final AttendanceReportRepository attendanceReportRepository;
    private final AtemschutzReminderLogRepository reminderLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public int warnDays(long unitId) {
        return atemschutzSettingsService.warnDays(unitId);
    }

    @Transactional
    public CarrierListResult listCarrierOverviews(long unitId, String filter) {
        atemschutzSettingsService.ensureSettings(unitId);
        syncCarriersFromAgt(unitId);
        List<AtemschutzCarrier> carriers = listCarriersForUnit(unitId);
        if (carriers.isEmpty()) {
            CarrierListStats emptyStats = new CarrierListStats(0, 0, 0, 0, 0, 0);
            return new CarrierListResult(
                    List.of(),
                    emptyStats,
                    emptyStats,
                    atemschutzSettingsService.agtCourseName(unitId),
                    atemschutzSettingsService.isAgtCourseConfigured(unitId));
        }
        List<Long> carrierIds = carriers.stream().map(AtemschutzCarrier::getId).toList();
        LocalDate today = LocalDate.now();
        Set<Long> csaPersonIds = listCsaPersonIds(unitId);
        Map<Long, AtemschutzFitnessRecord> latestG26 =
                latestRecordsByCarrier(carrierIds, AtemschutzFitnessType.G26_UNTERSUCHUNG);
        Map<Long, AtemschutzFitnessRecord> latestUebung =
                latestRecordsByCarrier(carrierIds, AtemschutzFitnessType.UEBUNG);
        Map<Long, AtemschutzFitnessRecord> latestStrecke =
                latestRecordsByCarrier(carrierIds, AtemschutzFitnessType.STRECKEN);
        Map<Long, AtemschutzFitnessRecord> latestCsa =
                latestRecordsByCarrier(carrierIds, AtemschutzFitnessType.CSA);
        ReminderLookup reminders = loadReminderLookup(carrierIds);
        List<CarrierOverview> all = new ArrayList<>();
        for (AtemschutzCarrier carrier : carriers) {
            Map<AtemschutzFitnessType, FitnessStatusView> summaries = new EnumMap<>(AtemschutzFitnessType.class);
            summaries.put(
                    AtemschutzFitnessType.G26_UNTERSUCHUNG,
                    toFitnessView(
                            latestG26.get(carrier.getId()),
                            atemschutzSettingsService.warnDays(unitId, AtemschutzFitnessType.G26_UNTERSUCHUNG),
                            today,
                            carrier.getId(),
                            AtemschutzFitnessType.G26_UNTERSUCHUNG,
                            reminders));
            summaries.put(
                    AtemschutzFitnessType.UEBUNG,
                    toFitnessView(
                            latestUebung.get(carrier.getId()),
                            atemschutzSettingsService.warnDays(unitId, AtemschutzFitnessType.UEBUNG),
                            today,
                            carrier.getId(),
                            AtemschutzFitnessType.UEBUNG,
                            reminders));
            summaries.put(
                    AtemschutzFitnessType.STRECKEN,
                    toFitnessView(
                            latestStrecke.get(carrier.getId()),
                            atemschutzSettingsService.warnDays(unitId, AtemschutzFitnessType.STRECKEN),
                            today,
                            carrier.getId(),
                            AtemschutzFitnessType.STRECKEN,
                            reminders));
            summaries.put(
                    AtemschutzFitnessType.CSA,
                    toFitnessView(
                            latestCsa.get(carrier.getId()),
                            atemschutzSettingsService.warnDays(unitId, AtemschutzFitnessType.CSA),
                            today,
                            carrier.getId(),
                            AtemschutzFitnessType.CSA,
                            reminders));
            CarrierTauglichkeitStatus tauglichkeit = computeTauglichkeit(summaries, carrier.getStatus());
            boolean csaEligible = csaPersonIds.contains(carrier.getPerson().getId());
            boolean csaTauglich = isCsaTauglich(csaEligible, summaries.get(AtemschutzFitnessType.CSA));
            all.add(new CarrierOverview(
                    carrier,
                    summaries.get(AtemschutzFitnessType.G26_UNTERSUCHUNG),
                    summaries,
                    tauglichkeit,
                    csaEligible,
                    csaTauglich));
        }
        List<CarrierOverview> statsCarriers = all.stream()
                .filter(AtemschutzService::countsForAtemschutzStats)
                .toList();
        CarrierListStats stats = computeStats(statsCarriers);
        CarrierListStats statsAll = computeStats(all.stream()
                .filter(row -> PersonMembership.isCurrentlyMember(row.carrier().getPerson()))
                .toList());
        List<CarrierOverview> filtered = applyFilter(all, filter);
        return new CarrierListResult(
                filtered,
                stats,
                statsAll,
                atemschutzSettingsService.agtCourseName(unitId),
                atemschutzSettingsService.isAgtCourseConfigured(unitId));
    }

    /** Ausgetretene bleiben in der Liste, zählen aber nicht für Kennzahlen/Warnungen. */
    private static boolean countsForAtemschutzStats(CarrierOverview row) {
        if (row == null || row.carrier() == null) {
            return false;
        }
        if (row.carrier().getStatus() != AtemschutzCarrierStatus.ACTIVE) {
            return false;
        }
        return PersonMembership.isCurrentlyMember(row.carrier().getPerson());
    }

    @Transactional(readOnly = true)
    public AtemschutzCarrier requireCarrier(long carrierId) {
        if (!testModeService.isEnabled()) {
            return carrierRepository
                    .findByIdAndTestData(carrierId, false)
                    .orElseThrow(() -> new IllegalArgumentException("Geräteträger nicht gefunden"));
        }
        Optional<AtemschutzCarrier> testRow = carrierRepository.findByIdAndTestData(carrierId, true);
        if (testRow.isPresent()) {
            return testRow.get();
        }
        AtemschutzCarrier prod = carrierRepository
                .findByIdAndTestData(carrierId, false)
                .orElseThrow(() -> new IllegalArgumentException("Geräteträger nicht gefunden"));
        return carrierRepository.findShadowByProductionSourceId(prod.getId()).orElse(prod);
    }

    @Transactional
    public CarrierDetailView loadCarrierDetail(long carrierId) {
        AtemschutzCarrier carrier = requireCarrier(carrierId);
        reconcileAttendancePaFitness(carrier);
        List<AtemschutzFitnessRecord> records = fitnessRecordsForCarrier(carrier.getId());
        long unitId = carrier.getUnit().getId();
        LocalDate today = LocalDate.now();
        ReminderLookup reminders = loadReminderLookup(List.of(carrier.getId()));
        Map<AtemschutzFitnessType, FitnessStatusView> summaries = new EnumMap<>(AtemschutzFitnessType.class);
        for (AtemschutzFitnessType type : AtemschutzFitnessType.values()) {
            AtemschutzFitnessRecord latest = records.stream()
                    .filter(r -> r.getRecordType() == type)
                    .max(Comparator.comparing(AtemschutzFitnessRecord::getValidUntil)
                            .thenComparing(AtemschutzFitnessRecord::getId))
                    .orElse(null);
            summaries.put(
                    type,
                    toFitnessView(
                            latest,
                            atemschutzSettingsService.warnDays(unitId, type),
                            today,
                            carrier.getId(),
                            type,
                            reminders));
        }
        List<FitnessRecordView> recordViews = collapseUebungRecords(records).stream()
                .map(this::toRecordView)
                .toList();
        return new CarrierDetailView(carrier, summaries, recordViews);
    }

    /**
     * Nachzug: PA-Markierungen aus Anwesenheitslisten in Übung/Einsatz-Nachweise übernehmen
     * (auch wenn die Markierung früher gesetzt wurde, bevor der Sync existierte).
     */
    private void reconcileAttendancePaFitness(AtemschutzCarrier carrier) {
        if (carrier == null || carrier.getPerson() == null || carrier.getUnit() == null) {
            return;
        }
        long unitId = carrier.getUnit().getId();
        long personId = carrier.getPerson().getId();
        boolean testData = testModeService.isEnabled();
        for (Integer year : attendanceReportRepository.findDistinctYearsByUnitId(unitId, testData)) {
            if (year == null) {
                continue;
            }
            LocalDate yearStart = LocalDate.of(year, 1, 1);
            LocalDate yearEnd = yearStart.plusYears(1);
            for (AttendanceReport report :
                    attendanceReportRepository.findByUnitIdAndYear(unitId, yearStart, yearEnd, testData)) {
                boolean hasPa = crewJsonContainsPaPerson(report.getCrewAssignmentsJson(), personId);
                Optional<AtemschutzFitnessRecord> existing = fitnessRecordRepository.findBySourceRefAndPersonId(
                        FITNESS_SOURCE_ATTENDANCE_REPORT, report.getId(), personId, testData);
                if (!hasPa) {
                    existing.ifPresent(fitnessRecordRepository::delete);
                    continue;
                }
                if (report.getEventDate() == null) {
                    continue;
                }
                String label = report.getTitle() != null && !report.getTitle().isBlank()
                        ? report.getTitle().trim()
                        : "Anwesenheitsliste";
                upsertPaSourceRecord(
                        carrier,
                        FITNESS_SOURCE_ATTENDANCE_REPORT,
                        report.getId(),
                        report.getEventDate(),
                        label,
                        null,
                        testData);
            }
        }
    }

    /**
     * In der Nachweise-Tabelle nur den neuesten Übung/Einsatz-Eintrag zeigen;
     * die vollständige PA-Historie steht in der Tabelle darunter.
     */
    private static List<AtemschutzFitnessRecord> collapseUebungRecords(List<AtemschutzFitnessRecord> records) {
        List<AtemschutzFitnessRecord> result = new ArrayList<>(records.size());
        boolean latestUebungAdded = false;
        for (AtemschutzFitnessRecord record : records) {
            if (record.getRecordType() == AtemschutzFitnessType.UEBUNG) {
                if (latestUebungAdded) {
                    continue;
                }
                latestUebungAdded = true;
            }
            result.add(record);
        }
        return result;
    }

    /**
     * Einsätze und Anwesenheitslisten im Jahr, bei denen die Person PA getragen hat
     * (nur freigegebene/archivierte Berichte).
     */
    @Transactional(readOnly = true)
    public List<PaEinsatzRow> listPaEinsaetze(long unitId, long personId, int year, String returnUrl) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year + 1, 1, 1);
        boolean includeTest = testModeService.isEnabled();
        List<IncidentReportStatus> statuses =
                List.of(IncidentReportStatus.FREIGEGEBEN, IncidentReportStatus.ARCHIVIERT);

        List<PaEinsatzRow> rows = new ArrayList<>();

        for (IncidentReport report : incidentReportPersonnelRepository.findPaReportsByPersonAndYear(
                personId, unitId, yearStart, yearEnd, statuses, includeTest)) {
            String label = report.getStichwort() != null && !report.getStichwort().isBlank()
                    ? report.getStichwort().trim()
                    : "Einsatzbericht";
            rows.add(new PaEinsatzRow(
                    "einsatz",
                    "Einsatz",
                    report.getIncidentDate(),
                    label,
                    buildReportViewUrl("/berichte/einsatzberichte/" + report.getId(), unitId, returnUrl)));
        }

        for (AttendanceReport report :
                attendanceReportRepository.findByUnitIdAndYear(unitId, yearStart, yearEnd, includeTest)) {
            if (report.getStatus() != IncidentReportStatus.FREIGEGEBEN
                    && report.getStatus() != IncidentReportStatus.ARCHIVIERT) {
                continue;
            }
            if (!crewJsonContainsPaPerson(report.getCrewAssignmentsJson(), personId)) {
                continue;
            }
            String label = report.getTitle() != null && !report.getTitle().isBlank()
                    ? report.getTitle().trim()
                    : "Anwesenheitsliste";
            rows.add(new PaEinsatzRow(
                    "anwesenheit",
                    "Anwesenheitsliste",
                    report.getEventDate(),
                    label,
                    buildReportViewUrl("/berichte/anwesenheitslisten/" + report.getId(), unitId, returnUrl)));
        }

        rows.sort(Comparator.comparing(PaEinsatzRow::date, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(PaEinsatzRow::label, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<Integer> listPaEinsatzYears(long unitId, long personId) {
        boolean includeTest = testModeService.isEnabled();
        List<IncidentReportStatus> statuses =
                List.of(IncidentReportStatus.FREIGEGEBEN, IncidentReportStatus.ARCHIVIERT);
        Set<Integer> years = new HashSet<>(incidentReportPersonnelRepository.findDistinctPaYearsByPerson(
                personId, unitId, statuses, includeTest));
        for (Integer year : attendanceReportRepository.findDistinctYearsByUnitId(unitId, includeTest)) {
            if (year == null || years.contains(year)) {
                continue;
            }
            LocalDate yearStart = LocalDate.of(year, 1, 1);
            LocalDate yearEnd = yearStart.plusYears(1);
            for (AttendanceReport report :
                    attendanceReportRepository.findByUnitIdAndYear(unitId, yearStart, yearEnd, includeTest)) {
                if (report.getStatus() != IncidentReportStatus.FREIGEGEBEN
                        && report.getStatus() != IncidentReportStatus.ARCHIVIERT) {
                    continue;
                }
                if (crewJsonContainsPaPerson(report.getCrewAssignmentsJson(), personId)) {
                    years.add(year);
                    break;
                }
            }
        }
        return de.feuerwehr.manager.util.YearFilterSupport.descendingYears(years);
    }

    private boolean crewJsonContainsPaPerson(String json, long personId) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                return false;
            }
            for (JsonNode assignment : root) {
                JsonNode paIds = assignment.get("paPersonIds");
                if (paIds == null || !paIds.isArray()) {
                    continue;
                }
                for (JsonNode idNode : paIds) {
                    if (idNode != null && idNode.isNumber() && idNode.longValue() == personId) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static String buildReportViewUrl(String path, long unitId, String returnUrl) {
        String url = path + "?unit=" + unitId;
        if (returnUrl != null && !returnUrl.isBlank()) {
            url += "&returnUrl=" + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
        }
        return url;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncCarriersFromAgt(long unitId) {
        Long courseId = atemschutzSettingsService.agtCourseId(unitId).orElse(null);
        if (courseId == null) {
            return;
        }
        List<Person> agtPersons = listAgtPersons(unitId, courseId);
        Set<Long> agtPersonIds = agtPersons.stream().map(Person::getId).collect(Collectors.toCollection(HashSet::new));
        Unit unit = unitRepository
                .findVisibleById(unitId, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden"));
        for (Person person : agtPersons) {
            ensureCarrierForPerson(unit, person);
        }
        for (AtemschutzCarrier carrier : listCarriersForUnit(unitId)) {
            if (!agtPersonIds.contains(carrier.getPerson().getId())) {
                if (testModeService.isEnabled() && !carrier.isTestData()) {
                    continue;
                }
                carrierRepository.delete(carrier);
            }
        }
    }

    private void ensureCarrierForPerson(Unit unit, Person person) {
        if (carrierRepository.existsByPersonId(person.getId())) {
            return;
        }
        AtemschutzCarrier carrier = new AtemschutzCarrier();
        carrier.setUnit(unit);
        carrier.setPerson(person);
        carrier.setStatus(AtemschutzCarrierStatus.ACTIVE);
        carrier.setTestData(testModeService.isEnabled());
        carrierRepository.save(carrier);
    }

    @Transactional
    public AtemschutzCarrier updateCarrier(long carrierId, AtemschutzCarrierStatus status, String notes) {
        AtemschutzCarrier carrier = writableCarrier(requireCarrier(carrierId));
        if (status != null) {
            carrier.setStatus(status);
        }
        carrier.setNotes(blankToNull(notes));
        return carrierRepository.save(carrier);
    }

    @Transactional
    public void removeCarrier(long carrierId) {
        AtemschutzCarrier carrier = requireCarrier(carrierId);
        if (testModeService.isEnabled() && !carrier.isTestData()) {
            throw new IllegalArgumentException("Produktiv-Geräteträger können im Testmodus nicht gelöscht werden.");
        }
        carrierRepository.delete(carrier);
    }

    @Transactional
    public AtemschutzFitnessRecord addFitnessRecord(
            long carrierId, AtemschutzFitnessType type, LocalDate validFrom, long createdByUserId) {
        AtemschutzCarrier carrier = requireCarrier(carrierId);
        if (validFrom == null) {
            throw new IllegalArgumentException("Datum ist erforderlich.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Nachweis-Typ fehlt.");
        }
        LocalDate validUntil = computeValidUntil(type, validFrom, carrier.getPerson().getBirthdate());
        User createdBy = userRepository
                .findById(createdByUserId)
                .orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden."));
        AtemschutzFitnessRecord record = new AtemschutzFitnessRecord();
        record.setCarrier(carrier);
        record.setRecordType(type);
        record.setValidFrom(validFrom);
        record.setValidUntil(validUntil);
        record.setCreatedBy(createdBy);
        record.setTestData(testModeService.isEnabled());
        return fitnessRecordRepository.save(record);
    }

    @Transactional
    public int bulkAddFitnessRecords(
            long unitId,
            List<Long> carrierIds,
            AtemschutzFitnessType type,
            LocalDate validFrom,
            long createdByUserId) {
        if (carrierIds == null || carrierIds.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens einen Geräteträger auswählen.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Nachweis-Typ fehlt.");
        }
        if (validFrom == null) {
            throw new IllegalArgumentException("Datum ist erforderlich.");
        }
        int saved = 0;
        for (Long carrierId : carrierIds) {
            if (carrierId == null || carrierId <= 0) {
                continue;
            }
            AtemschutzCarrier carrier = requireCarrier(carrierId);
            if (carrier.getUnit().getId() != unitId) {
                throw new IllegalArgumentException("Geräteträger gehört nicht zu dieser Einheit.");
            }
            addFitnessRecord(carrierId, type, validFrom, createdByUserId);
            saved++;
        }
        if (saved == 0) {
            throw new IllegalArgumentException("Keine gültigen Geräteträger ausgewählt.");
        }
        return saved;
    }

    public static LocalDate computeValidUntil(
            AtemschutzFitnessType type, LocalDate validFrom, LocalDate birthdate) {
        if (validFrom == null) {
            throw new IllegalArgumentException("Datum ist erforderlich.");
        }
        int years =
                switch (type) {
                    case STRECKEN, UEBUNG, CSA -> 1;
                    case G26_UNTERSUCHUNG -> {
                        if (birthdate == null) {
                            yield 1;
                        }
                        int age = Period.between(birthdate, validFrom).getYears();
                        yield age < 50 ? 3 : 1;
                    }
                };
        return validFrom.plusYears(years);
    }

    @Transactional
    public void deleteFitnessRecord(long recordId) {
        AtemschutzFitnessRecord record = fitnessRecordRepository
                .findByIdAndTestData(recordId, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Nachweis nicht gefunden"));
        fitnessRecordRepository.delete(record);
    }

    /** PA-Zuordnung im Einsatzbericht → Übung/Einsatz-Nachweis; entfernt verwaiste Einträge. */
    @Transactional
    public void syncIncidentPaFitnessRecords(
            long unitId,
            long reportId,
            Set<Long> paPersonIds,
            LocalDate incidentDate,
            String sourceLabel,
            Long createdByUserId) {
        syncPaFitnessRecords(
                unitId,
                FITNESS_SOURCE_INCIDENT_REPORT,
                reportId,
                paPersonIds,
                incidentDate,
                sourceLabel,
                createdByUserId);
    }

    /** PA-Zuordnung in der Anwesenheitsliste → Übung/Einsatz-Nachweis; entfernt verwaiste Einträge. */
    @Transactional
    public void syncAttendancePaFitnessRecords(
            long unitId,
            long reportId,
            Set<Long> paPersonIds,
            LocalDate eventDate,
            String sourceLabel,
            Long createdByUserId) {
        syncPaFitnessRecords(
                unitId,
                FITNESS_SOURCE_ATTENDANCE_REPORT,
                reportId,
                paPersonIds,
                eventDate,
                sourceLabel,
                createdByUserId);
    }

    @Transactional
    public void deleteIncidentPaFitnessRecords(long reportId) {
        deletePaFitnessRecords(FITNESS_SOURCE_INCIDENT_REPORT, reportId);
    }

    @Transactional
    public void deleteAttendancePaFitnessRecords(long reportId) {
        deletePaFitnessRecords(FITNESS_SOURCE_ATTENDANCE_REPORT, reportId);
    }

    @Transactional
    public void syncIncidentCsaFitnessRecords(
            long unitId,
            long reportId,
            Set<Long> csaPersonIds,
            LocalDate incidentDate,
            String sourceLabel,
            Long createdByUserId) {
        syncCsaFitnessRecords(
                unitId,
                FITNESS_SOURCE_INCIDENT_REPORT,
                reportId,
                csaPersonIds,
                incidentDate,
                sourceLabel,
                createdByUserId);
    }

    @Transactional
    public void syncAttendanceCsaFitnessRecords(
            long unitId,
            long reportId,
            Set<Long> csaPersonIds,
            LocalDate eventDate,
            String sourceLabel,
            Long createdByUserId) {
        syncCsaFitnessRecords(
                unitId,
                FITNESS_SOURCE_ATTENDANCE_REPORT,
                reportId,
                csaPersonIds,
                eventDate,
                sourceLabel,
                createdByUserId);
    }

    @Transactional
    public void deleteIncidentCsaFitnessRecords(long reportId) {
        deleteCsaFitnessRecords(FITNESS_SOURCE_INCIDENT_REPORT, reportId);
    }

    @Transactional
    public void deleteAttendanceCsaFitnessRecords(long reportId) {
        deleteCsaFitnessRecords(FITNESS_SOURCE_ATTENDANCE_REPORT, reportId);
    }

    /** Personen, die als Atemschutzgeräteträger PA markieren dürfen. */
    @Transactional(readOnly = true)
    public Set<Long> listPaEligiblePersonIds(long unitId) {
        return listCarriersForUnit(unitId).stream()
                .filter(carrier -> carrier.getStatus() == AtemschutzCarrierStatus.ACTIVE)
                .map(carrier -> carrier.getPerson().getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Personen mit CSA-Lehrgang (für CSA-Markierung in Berichten). */
    @Transactional(readOnly = true)
    public Set<Long> listCsaEligiblePersonIds(long unitId) {
        return listCsaPersonIds(unitId);
    }

    /** JSON-Array der Personen-IDs für Chip-Eligibility im Kräfte-Board. */
    public static String personIdsJson(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "[]";
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    private void syncPaFitnessRecords(
            long unitId,
            String sourceRefType,
            long reportId,
            Set<Long> paPersonIds,
            LocalDate eventDate,
            String sourceLabel,
            Long createdByUserId) {
        if (eventDate == null || sourceRefType == null || sourceRefType.isBlank()) {
            return;
        }
        boolean testData = testModeService.isEnabled();
        Set<Long> desiredPersonIds = paPersonIds != null ? paPersonIds : Set.of();
        List<AtemschutzFitnessRecord> existing =
                fitnessRecordRepository.findBySourceRefTypeAndSourceRefId(sourceRefType, reportId, testData);
        for (AtemschutzFitnessRecord record : existing) {
            if (record.getRecordType() != AtemschutzFitnessType.UEBUNG) {
                continue;
            }
            long personId = record.getCarrier().getPerson().getId();
            if (!desiredPersonIds.contains(personId)) {
                fitnessRecordRepository.delete(record);
            }
        }
        if (desiredPersonIds.isEmpty()) {
            return;
        }
        User createdBy = resolveCreatedBy(createdByUserId);
        String label = blankToNull(sourceLabel);
        for (Long personId : desiredPersonIds) {
            if (personId == null || personId <= 0) {
                continue;
            }
            carrierRepository
                    .findByPersonIdAndTestData(personId, testData)
                    .filter(carrier -> carrier.getUnit().getId() == unitId)
                    .ifPresent(carrier -> upsertPaSourceRecord(
                            carrier, sourceRefType, reportId, eventDate, label, createdBy, testData));
        }
    }

    private void deletePaFitnessRecords(String sourceRefType, long reportId) {
        boolean testData = testModeService.isEnabled();
        List<AtemschutzFitnessRecord> existing =
                fitnessRecordRepository.findBySourceRefTypeAndSourceRefId(sourceRefType, reportId, testData);
        List<AtemschutzFitnessRecord> uebungOnly = existing.stream()
                .filter(record -> record.getRecordType() == AtemschutzFitnessType.UEBUNG)
                .toList();
        if (!uebungOnly.isEmpty()) {
            fitnessRecordRepository.deleteAll(uebungOnly);
        }
    }

    private void upsertPaSourceRecord(
            AtemschutzCarrier carrier,
            String sourceRefType,
            long reportId,
            LocalDate eventDate,
            String sourceLabel,
            User createdBy,
            boolean testData) {
        long personId = carrier.getPerson().getId();
        Optional<AtemschutzFitnessRecord> existing = fitnessRecordRepository.findBySourceRefAndPersonIdAndType(
                sourceRefType, reportId, personId, AtemschutzFitnessType.UEBUNG, testData);
        LocalDate validUntil =
                computeValidUntil(AtemschutzFitnessType.UEBUNG, eventDate, carrier.getPerson().getBirthdate());
        if (existing.isPresent()) {
            AtemschutzFitnessRecord record = existing.get();
            boolean changed = false;
            if (!Objects.equals(record.getValidFrom(), eventDate)) {
                record.setValidFrom(eventDate);
                record.setValidUntil(validUntil);
                changed = true;
            }
            if (!Objects.equals(record.getSourceLabel(), sourceLabel)) {
                record.setSourceLabel(sourceLabel);
                changed = true;
            }
            if (changed) {
                fitnessRecordRepository.save(record);
            }
            return;
        }
        AtemschutzFitnessRecord record = new AtemschutzFitnessRecord();
        record.setCarrier(carrier);
        record.setRecordType(AtemschutzFitnessType.UEBUNG);
        record.setValidFrom(eventDate);
        record.setValidUntil(validUntil);
        record.setCreatedBy(createdBy);
        record.setTestData(testData);
        record.setSourceLabel(sourceLabel);
        record.setSourceRefType(sourceRefType);
        record.setSourceRefId(reportId);
        fitnessRecordRepository.save(record);
    }

    private void syncCsaFitnessRecords(
            long unitId,
            String sourceRefType,
            long reportId,
            Set<Long> csaPersonIds,
            LocalDate eventDate,
            String sourceLabel,
            Long createdByUserId) {
        if (eventDate == null || sourceRefType == null || sourceRefType.isBlank()) {
            return;
        }
        boolean testData = testModeService.isEnabled();
        Set<Long> eligible = listCsaPersonIds(unitId);
        Set<Long> desiredPersonIds = new LinkedHashSet<>();
        if (csaPersonIds != null) {
            for (Long personId : csaPersonIds) {
                if (personId != null && personId > 0 && eligible.contains(personId)) {
                    desiredPersonIds.add(personId);
                }
            }
        }
        List<AtemschutzFitnessRecord> existing =
                fitnessRecordRepository.findBySourceRefTypeAndSourceRefId(sourceRefType, reportId, testData);
        for (AtemschutzFitnessRecord record : existing) {
            if (record.getRecordType() != AtemschutzFitnessType.CSA) {
                continue;
            }
            long personId = record.getCarrier().getPerson().getId();
            if (!desiredPersonIds.contains(personId)) {
                fitnessRecordRepository.delete(record);
            }
        }
        if (desiredPersonIds.isEmpty()) {
            return;
        }
        User createdBy = resolveCreatedBy(createdByUserId);
        String label = blankToNull(sourceLabel);
        for (Long personId : desiredPersonIds) {
            carrierRepository
                    .findByPersonIdAndTestData(personId, testData)
                    .filter(carrier -> carrier.getUnit().getId() == unitId)
                    .ifPresent(carrier -> upsertCsaSourceRecord(
                            carrier, sourceRefType, reportId, eventDate, label, createdBy, testData));
        }
    }

    private void deleteCsaFitnessRecords(String sourceRefType, long reportId) {
        boolean testData = testModeService.isEnabled();
        List<AtemschutzFitnessRecord> existing =
                fitnessRecordRepository.findBySourceRefTypeAndSourceRefId(sourceRefType, reportId, testData);
        List<AtemschutzFitnessRecord> csaOnly = existing.stream()
                .filter(record -> record.getRecordType() == AtemschutzFitnessType.CSA)
                .toList();
        if (!csaOnly.isEmpty()) {
            fitnessRecordRepository.deleteAll(csaOnly);
        }
    }

    private void upsertCsaSourceRecord(
            AtemschutzCarrier carrier,
            String sourceRefType,
            long reportId,
            LocalDate eventDate,
            String sourceLabel,
            User createdBy,
            boolean testData) {
        long personId = carrier.getPerson().getId();
        Optional<AtemschutzFitnessRecord> existing = fitnessRecordRepository.findBySourceRefAndPersonIdAndType(
                sourceRefType, reportId, personId, AtemschutzFitnessType.CSA, testData);
        LocalDate validUntil =
                computeValidUntil(AtemschutzFitnessType.CSA, eventDate, carrier.getPerson().getBirthdate());
        if (existing.isPresent()) {
            AtemschutzFitnessRecord record = existing.get();
            boolean changed = false;
            if (!Objects.equals(record.getValidFrom(), eventDate)) {
                record.setValidFrom(eventDate);
                record.setValidUntil(validUntil);
                changed = true;
            }
            if (!Objects.equals(record.getSourceLabel(), sourceLabel)) {
                record.setSourceLabel(sourceLabel);
                changed = true;
            }
            if (changed) {
                fitnessRecordRepository.save(record);
            }
            return;
        }
        AtemschutzFitnessRecord record = new AtemschutzFitnessRecord();
        record.setCarrier(carrier);
        record.setRecordType(AtemschutzFitnessType.CSA);
        record.setValidFrom(eventDate);
        record.setValidUntil(validUntil);
        record.setCreatedBy(createdBy);
        record.setTestData(testData);
        record.setSourceLabel(sourceLabel);
        record.setSourceRefType(sourceRefType);
        record.setSourceRefId(reportId);
        fitnessRecordRepository.save(record);
    }

    private User resolveCreatedBy(Long createdByUserId) {
        if (createdByUserId == null || createdByUserId <= 0) {
            return null;
        }
        return userRepository.findById(createdByUserId).orElse(null);
    }

    public static AtemschutzFitnessLevel computeLevel(LocalDate validUntil, int warnDays, LocalDate today) {
        if (validUntil == null) {
            return AtemschutzFitnessLevel.MISSING;
        }
        if (validUntil.isBefore(today)) {
            return AtemschutzFitnessLevel.OVERDUE;
        }
        if (!validUntil.isAfter(today.plusDays(warnDays))) {
            return AtemschutzFitnessLevel.WARN;
        }
        return AtemschutzFitnessLevel.OK;
    }

    private List<AtemschutzCarrier> listCarriersForUnit(long unitId) {
        if (!testModeService.isEnabled()) {
            return carrierRepository.findByUnitId(unitId, false);
        }
        List<AtemschutzCarrier> production = carrierRepository.findByUnitId(unitId, false);
        List<AtemschutzCarrier> testRows = carrierRepository.findByUnitId(unitId, true);
        return TestModeDataMerge.mergeByProductionSource(
                production,
                testRows,
                AtemschutzCarrier::getProductionSourceId,
                AtemschutzCarrier::getId,
                Comparator.comparing((AtemschutzCarrier c) -> c.getPerson().getLastName())
                        .thenComparing(c -> c.getPerson().getFirstName()));
    }

    private List<Person> listAgtPersons(long unitId, long courseId) {
        List<Person> production =
                completionRepository.findPersonsWithCompletedCourseId(unitId, false, courseId);
        if (!testModeService.isEnabled()) {
            return production;
        }
        List<Person> testRows = completionRepository.findPersonsWithCompletedCourseId(unitId, true, courseId);
        return TestModeDataMerge.mergeByProductionSource(
                production,
                testRows,
                Person::getProductionSourceId,
                Person::getId,
                Comparator.comparing(Person::getLastName).thenComparing(Person::getFirstName));
    }

    private List<AtemschutzFitnessRecord> fitnessRecordsForCarrier(long carrierId) {
        if (!testModeService.isEnabled()) {
            return fitnessRecordRepository.findByCarrierId(carrierId, false);
        }
        List<AtemschutzFitnessRecord> records = new ArrayList<>();
        records.addAll(fitnessRecordRepository.findByCarrierId(carrierId, false));
        records.addAll(fitnessRecordRepository.findByCarrierId(carrierId, true));
        records.sort(Comparator.comparing(AtemschutzFitnessRecord::getValidUntil)
                .thenComparing(AtemschutzFitnessRecord::getId)
                .reversed());
        return records;
    }

    private AtemschutzCarrier writableCarrier(AtemschutzCarrier viewed) {
        if (!testModeService.isEnabled() || viewed.isTestData()) {
            return viewed;
        }
        return carrierRepository
                .findShadowByProductionSourceId(viewed.getId())
                .orElseGet(() -> carrierRepository.save(copyCarrierToShadow(viewed)));
    }

    private AtemschutzCarrier copyCarrierToShadow(AtemschutzCarrier prod) {
        AtemschutzCarrier shadow = new AtemschutzCarrier();
        shadow.setUnit(prod.getUnit());
        shadow.setPerson(prod.getPerson());
        shadow.setStatus(prod.getStatus());
        shadow.setNotes(prod.getNotes());
        shadow.setTestData(true);
        shadow.setProductionSourceId(prod.getId());
        return shadow;
    }

    private Map<Long, AtemschutzFitnessRecord> latestRecordsByCarrier(
            List<Long> carrierIds, AtemschutzFitnessType type) {
        if (carrierIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AtemschutzFitnessRecord> result = indexLatestRecords(
                fitnessRecordRepository.findByCarrierIdsAndType(carrierIds, type, false));
        if (testModeService.isEnabled()) {
            result.putAll(indexLatestRecords(
                    fitnessRecordRepository.findByCarrierIdsAndType(carrierIds, type, true)));
        }
        return result;
    }

    private static Map<Long, AtemschutzFitnessRecord> indexLatestRecords(List<AtemschutzFitnessRecord> records) {
        Map<Long, AtemschutzFitnessRecord> result = new HashMap<>();
        for (AtemschutzFitnessRecord record : records) {
            result.putIfAbsent(record.getCarrier().getId(), record);
        }
        return result;
    }

    private FitnessStatusView toFitnessView(
            AtemschutzFitnessRecord record,
            int warnDays,
            LocalDate today,
            long carrierId,
            AtemschutzFitnessType type,
            ReminderLookup reminders) {
        if (record == null) {
            return new FitnessStatusView(AtemschutzFitnessLevel.MISSING, null, null)
                    .withReminder(false, false, reminders.lastSentAt(carrierId, type));
        }
        AtemschutzFitnessLevel level = computeLevel(record.getValidUntil(), warnDays, today);
        boolean eligible = level == AtemschutzFitnessLevel.WARN || level == AtemschutzFitnessLevel.OVERDUE;
        AtemschutzReminderMailKind mailKind =
                level == AtemschutzFitnessLevel.WARN
                        ? AtemschutzReminderMailKind.WARNUNG
                        : level == AtemschutzFitnessLevel.OVERDUE ? AtemschutzReminderMailKind.ABGELAUFEN : null;
        boolean sent = eligible
                && mailKind != null
                && reminders.sentFor(carrierId, type, mailKind, record.getValidUntil());
        return new FitnessStatusView(level, record.getValidUntil(), record.getValidFrom())
                .withReminder(eligible, sent, reminders.lastSentAt(carrierId, type));
    }

    private ReminderLookup loadReminderLookup(Collection<Long> carrierIds) {
        if (carrierIds == null || carrierIds.isEmpty()) {
            return ReminderLookup.empty();
        }
        List<AtemschutzReminderLog> logs = reminderLogRepository.findByCarrier_IdIn(carrierIds);
        Map<ReminderExactKey, Boolean> sentExact = new HashMap<>();
        Map<Long, Map<AtemschutzFitnessType, Instant>> lastSent = new HashMap<>();
        for (AtemschutzReminderLog logEntry : logs) {
            if (logEntry.getCarrier() == null || logEntry.getCarrier().getId() == null) {
                continue;
            }
            if (!logEntry.isCarrierNotified()) {
                continue;
            }
            long carrierId = logEntry.getCarrier().getId();
            sentExact.put(
                    new ReminderExactKey(
                            carrierId, logEntry.getFitnessType(), logEntry.getMailKind(), logEntry.getValidUntil()),
                    Boolean.TRUE);
            Instant sentAt = logEntry.getSentAt();
            if (sentAt == null) {
                continue;
            }
            Map<AtemschutzFitnessType, Instant> byType =
                    lastSent.computeIfAbsent(carrierId, ignored -> new EnumMap<>(AtemschutzFitnessType.class));
            Instant previous = byType.get(logEntry.getFitnessType());
            if (previous == null || sentAt.isAfter(previous)) {
                byType.put(logEntry.getFitnessType(), sentAt);
            }
        }
        return new ReminderLookup(sentExact, lastSent);
    }

    private record ReminderExactKey(
            long carrierId,
            AtemschutzFitnessType type,
            AtemschutzReminderMailKind mailKind,
            LocalDate validUntil) {}

    private record ReminderLookup(
            Map<ReminderExactKey, Boolean> sentExact,
            Map<Long, Map<AtemschutzFitnessType, Instant>> lastSent) {

        static ReminderLookup empty() {
            return new ReminderLookup(Map.of(), Map.of());
        }

        boolean sentFor(
                long carrierId,
                AtemschutzFitnessType type,
                AtemschutzReminderMailKind mailKind,
                LocalDate validUntil) {
            return sentExact.containsKey(new ReminderExactKey(carrierId, type, mailKind, validUntil));
        }

        Instant lastSentAt(long carrierId, AtemschutzFitnessType type) {
            Map<AtemschutzFitnessType, Instant> byType = lastSent.get(carrierId);
            return byType != null ? byType.get(type) : null;
        }
    }

    private FitnessRecordView toRecordView(AtemschutzFitnessRecord record) {
        int warnDays = atemschutzSettingsService.warnDays(
                record.getCarrier().getUnit().getId(), record.getRecordType());
        AtemschutzFitnessLevel level = computeLevel(record.getValidUntil(), warnDays, LocalDate.now());
        Long incidentReportId = null;
        Long attendanceReportId = null;
        if (FITNESS_SOURCE_INCIDENT_REPORT.equals(record.getSourceRefType()) && record.getSourceRefId() != null) {
            incidentReportId = record.getSourceRefId();
        } else if (FITNESS_SOURCE_ATTENDANCE_REPORT.equals(record.getSourceRefType())
                && record.getSourceRefId() != null) {
            attendanceReportId = record.getSourceRefId();
        }
        return new FitnessRecordView(
                record.getId(),
                record.getRecordType(),
                level,
                record.getValidFrom(),
                record.getValidUntil(),
                formatCreatedBy(record),
                blankToNull(record.getSourceLabel()),
                incidentReportId,
                attendanceReportId);
    }

    private static String formatCreatedBy(AtemschutzFitnessRecord record) {
        if (record.getCreatedBy() == null) {
            return null;
        }
        String name = record.getCreatedBy().getDisplayName();
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return record.getCreatedBy().getUsername();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static boolean isOverallTauglich(
            Map<AtemschutzFitnessType, FitnessStatusView> summaries, AtemschutzCarrierStatus status) {
        return computeTauglichkeit(summaries, status) == CarrierTauglichkeitStatus.TAUGLICH;
    }

    public static CarrierTauglichkeitStatus computeTauglichkeit(
            Map<AtemschutzFitnessType, FitnessStatusView> summaries, AtemschutzCarrierStatus status) {
        if (status != AtemschutzCarrierStatus.ACTIVE) {
            return CarrierTauglichkeitStatus.NICHT_TAUGLICH;
        }
        return switch (computePlanStatus(summaries)) {
            case TAUGLICH -> CarrierTauglichkeitStatus.TAUGLICH;
            case WARNUNG -> CarrierTauglichkeitStatus.WARNUNG;
            case UEBUNG_ABGELAUFEN -> CarrierTauglichkeitStatus.UEBUNG_ABGELAUFEN;
            case ABGELAUFEN -> CarrierTauglichkeitStatus.NICHT_TAUGLICH;
        };
    }

    private static List<CarrierOverview> applyFilter(List<CarrierOverview> carriers, String filter) {
        if (filter == null || filter.isBlank() || "all".equalsIgnoreCase(filter)) {
            return carriers;
        }
        List<CarrierOverview> activeMembers = carriers.stream()
                .filter(AtemschutzService::countsForAtemschutzStats)
                .toList();
        if ("tauglich".equalsIgnoreCase(filter)) {
            return activeMembers.stream()
                    .filter(row -> row.tauglichkeit() == CarrierTauglichkeitStatus.TAUGLICH)
                    .toList();
        }
        if ("warnung".equalsIgnoreCase(filter)) {
            return activeMembers.stream()
                    .filter(row -> row.tauglichkeit() == CarrierTauglichkeitStatus.WARNUNG)
                    .toList();
        }
        if ("uebung_abgelaufen".equalsIgnoreCase(filter) || "uebungabgelaufen".equalsIgnoreCase(filter)) {
            return activeMembers.stream()
                    .filter(row -> row.tauglichkeit() == CarrierTauglichkeitStatus.UEBUNG_ABGELAUFEN)
                    .toList();
        }
        if ("nicht_tauglich".equalsIgnoreCase(filter) || "nichttauglich".equalsIgnoreCase(filter)) {
            return activeMembers.stream()
                    .filter(row -> row.tauglichkeit() == CarrierTauglichkeitStatus.NICHT_TAUGLICH)
                    .toList();
        }
        if ("csa".equalsIgnoreCase(filter)) {
            return activeMembers.stream().filter(CarrierOverview::csaTauglich).toList();
        }
        return carriers;
    }

    @Transactional(readOnly = true)
    public UebungPlanResult planUebung(
            long unitId,
            LocalDate uebungsDatum,
            Set<AtemschutzPlanStatus> statusFilter,
            int limit) {
        if (uebungsDatum == null) {
            throw new IllegalArgumentException("Bitte ein Übungsdatum angeben.");
        }
        Set<AtemschutzPlanStatus> effectiveFilter = statusFilter == null || statusFilter.isEmpty()
                ? EnumSet.copyOf(AtemschutzPlanStatus.DEFAULT_SELECTED.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet()))
                : EnumSet.copyOf(statusFilter);

        CarrierListResult allCarriers = listCarrierOverviews(unitId, "all");
        List<UebungPlanRow> matches = new ArrayList<>();
        for (CarrierOverview row : allCarriers.carriers()) {
            if (row.carrier().getStatus() != AtemschutzCarrierStatus.ACTIVE) {
                continue;
            }
            if (!PersonMembership.isCurrentlyMember(row.carrier().getPerson())) {
                continue;
            }
            AtemschutzPlanStatus planStatus = computePlanStatus(row.summaries());
            if (!effectiveFilter.contains(planStatus)) {
                continue;
            }
            matches.add(new UebungPlanRow(row, planStatus));
        }

        matches.sort(Comparator.comparing(
                row -> row.overview().summaries().get(AtemschutzFitnessType.UEBUNG),
                Comparator.comparing(
                        view -> view != null && view.validUntil() != null ? view.validUntil() : LocalDate.MAX)));

        int effectiveLimit = limit > 0 ? limit : matches.size();
        List<UebungPlanRow> limited = matches.size() <= effectiveLimit ? matches : matches.subList(0, effectiveLimit);

        return new UebungPlanResult(uebungsDatum, limit, effectiveFilter, limited, matches.size());
    }

    public static AtemschutzPlanStatus computePlanStatus(Map<AtemschutzFitnessType, FitnessStatusView> summaries) {
        boolean streckeExpired = isPlanExpired(summaries.get(AtemschutzFitnessType.STRECKEN));
        boolean g26Expired = isPlanExpired(summaries.get(AtemschutzFitnessType.G26_UNTERSUCHUNG));
        boolean uebungExpired = isPlanExpired(summaries.get(AtemschutzFitnessType.UEBUNG));
        boolean streckeWarn = isPlanWarn(summaries.get(AtemschutzFitnessType.STRECKEN));
        boolean g26Warn = isPlanWarn(summaries.get(AtemschutzFitnessType.G26_UNTERSUCHUNG));
        boolean uebungWarn = isPlanWarn(summaries.get(AtemschutzFitnessType.UEBUNG));

        if (streckeExpired || g26Expired || uebungExpired) {
            if (uebungExpired && !streckeExpired && !g26Expired) {
                return AtemschutzPlanStatus.UEBUNG_ABGELAUFEN;
            }
            return AtemschutzPlanStatus.ABGELAUFEN;
        }
        if (streckeWarn || g26Warn || uebungWarn) {
            return AtemschutzPlanStatus.WARNUNG;
        }
        return AtemschutzPlanStatus.TAUGLICH;
    }

    private static boolean isPlanExpired(FitnessStatusView view) {
        if (view == null) {
            return true;
        }
        return view.level() == AtemschutzFitnessLevel.OVERDUE || view.level() == AtemschutzFitnessLevel.MISSING;
    }

    private static boolean isPlanWarn(FitnessStatusView view) {
        return view != null && view.level() == AtemschutzFitnessLevel.WARN;
    }

    private static CarrierListStats computeStats(List<CarrierOverview> carriers) {
        int tauglich = (int) carriers.stream()
                .filter(row -> row.tauglichkeit() == CarrierTauglichkeitStatus.TAUGLICH)
                .count();
        int warnung = (int) carriers.stream()
                .filter(row -> row.tauglichkeit() == CarrierTauglichkeitStatus.WARNUNG)
                .count();
        int uebungAbgelaufen = (int) carriers.stream()
                .filter(row -> row.tauglichkeit() == CarrierTauglichkeitStatus.UEBUNG_ABGELAUFEN)
                .count();
        int nichtTauglich = (int) carriers.stream()
                .filter(row -> row.tauglichkeit() == CarrierTauglichkeitStatus.NICHT_TAUGLICH)
                .count();
        int csaTauglich = (int) carriers.stream().filter(CarrierOverview::csaTauglich).count();
        return new CarrierListStats(carriers.size(), tauglich, warnung, uebungAbgelaufen, nichtTauglich, csaTauglich);
    }

    private Set<Long> listCsaPersonIds(long unitId) {
        Long courseId = atemschutzSettingsService.csaCourseId(unitId).orElse(null);
        if (courseId == null) {
            return Set.of();
        }
        return listAgtPersons(unitId, courseId).stream()
                .map(Person::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean isCsaTauglich(boolean csaEligible, FitnessStatusView csaView) {
        if (!csaEligible || csaView == null) {
            return false;
        }
        return csaView.level() == AtemschutzFitnessLevel.OK || csaView.level() == AtemschutzFitnessLevel.WARN;
    }

    public record UebungPlanResult(
            LocalDate uebungsDatum,
            int requestedLimit,
            Set<AtemschutzPlanStatus> statusFilter,
            List<UebungPlanRow> carriers,
            int totalMatches) {}

    public record UebungPlanRow(CarrierOverview overview, AtemschutzPlanStatus planStatus) {}

    public record CarrierListResult(
            List<CarrierOverview> carriers,
            CarrierListStats stats,
            CarrierListStats statsAll,
            String agtCourseName,
            boolean agtCourseConfigured) {}

    public record CarrierListStats(
            int total, int tauglich, int warnung, int uebungAbgelaufen, int nichtTauglich, int csaTauglich) {}

    public record CarrierOverview(
            AtemschutzCarrier carrier,
            FitnessStatusView g26,
            Map<AtemschutzFitnessType, FitnessStatusView> summaries,
            CarrierTauglichkeitStatus tauglichkeit,
            boolean csaEligible,
            boolean csaTauglich) {}

    public record CarrierDetailView(
            AtemschutzCarrier carrier,
            Map<AtemschutzFitnessType, FitnessStatusView> summaries,
            List<FitnessRecordView> records) {

        public boolean hasReminderEligible() {
            if (summaries == null || summaries.isEmpty()) {
                return false;
            }
            return summaries.values().stream().anyMatch(view -> view != null && view.reminderEligible());
        }

        public boolean anyEligibleReminderSent() {
            if (summaries == null || summaries.isEmpty()) {
                return false;
            }
            return summaries.values().stream()
                    .anyMatch(view -> view != null && view.reminderEligible() && view.reminderSent());
        }

        public String latestEligibleReminderLabel() {
            if (summaries == null || summaries.isEmpty()) {
                return null;
            }
            return summaries.values().stream()
                    .filter(view -> view != null && view.reminderEligible() && view.reminderLastSentAt() != null)
                    .max(Comparator.comparing(FitnessStatusView::reminderLastSentAt))
                    .map(FitnessStatusView::reminderLastSentLabel)
                    .orElse(null);
        }
    }

    public record FitnessStatusView(
            AtemschutzFitnessLevel level,
            LocalDate validUntil,
            LocalDate validFrom,
            boolean reminderEligible,
            boolean reminderSent,
            Instant reminderLastSentAt) {

        private static final DateTimeFormatter REMINDER_SENT_FMT =
                DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.GERMANY).withZone(ZoneId.of("Europe/Berlin"));

        public FitnessStatusView(AtemschutzFitnessLevel level, LocalDate validUntil, LocalDate validFrom) {
            this(level, validUntil, validFrom, false, false, null);
        }

        public FitnessStatusView withReminder(boolean eligible, boolean sent, Instant lastSentAt) {
            return new FitnessStatusView(level, validUntil, validFrom, eligible, sent, lastSentAt);
        }

        public String reminderLastSentLabel() {
            if (reminderLastSentAt == null) {
                return null;
            }
            return REMINDER_SENT_FMT.format(reminderLastSentAt) + " Uhr";
        }
    }

    public record FitnessRecordView(
            long id,
            AtemschutzFitnessType type,
            AtemschutzFitnessLevel level,
            LocalDate validFrom,
            LocalDate validUntil,
            String createdByDisplay,
            String sourceLabel,
            Long incidentReportId,
            Long attendanceReportId) {}

    public record PaEinsatzRow(
            String kind,
            String kindLabel,
            LocalDate date,
            String label,
            String viewUrl) {}
}
