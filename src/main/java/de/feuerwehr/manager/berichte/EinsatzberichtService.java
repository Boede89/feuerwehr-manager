package de.feuerwehr.manager.berichte;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.divera.DiveraAlarmDetailsMapper.DiveraAlarmDetails;
import de.feuerwehr.manager.divera.DiveraApiClient;
import static de.feuerwehr.manager.divera.DiveraIntegrationSupport.DIVERA_ZONE;

import de.feuerwehr.manager.divera.DiveraIntegrationSupport;
import de.feuerwehr.manager.atemschutz.AtemschutzService;
import de.feuerwehr.manager.divera.DiveraMappingService;
import de.feuerwehr.manager.divera.DiveraService;
import de.feuerwehr.manager.unit.UnitDiveraSettings;
import de.feuerwehr.manager.unit.UnitDiveraSettingsRepository;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.technik.Vehicle;
import de.feuerwehr.manager.technik.VehicleRepository;
import de.feuerwehr.manager.technik.VehicleTypes;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EinsatzberichtService {

    private static final TypeReference<List<CrewAssignmentPayload>> CREW_ASSIGNMENT_LIST =
            new TypeReference<>() {};

    private final IncidentReportRepository incidentReportRepository;
    private final IncidentReportPersonnelRepository incidentReportPersonnelRepository;
    private final IncidentReportVehicleRepository incidentReportVehicleRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final PersonalService personalService;
    private final PersonRepository personRepository;
    private final VehicleRepository vehicleRepository;
    private final TestModeService testModeService;
    private final BerichteSettingsService berichteSettingsService;
    private final DiveraApiClient diveraApiClient;
    private final DiveraService diveraService;
    private final DiveraMappingService diveraMappingService;
    private final AtemschutzService atemschutzService;
    private final UnitDiveraSettingsRepository diveraSettingsRepository;
    private final ObjectMapper objectMapper;

    public List<IncidentReport> listByUnit(long unitId) {
        return incidentReportRepository.findByUnitIdOrderByDateDesc(unitId);
    }

    public List<Person> listPersonsForForm(long unitId) {
        return personalService.listPersons(unitId);
    }

    public List<Vehicle> listVehiclesForForm(long unitId) {
        return vehicleRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(
                unitId, testModeService.isEnabled());
    }

    public List<String> listKnownStichworte(long unitId) {
        return incidentReportRepository.findDistinctStichworteByUnitId(unitId);
    }

    public KraefteFahrzeugeState buildKraefteFahrzeugeState(long unitId, Long reportId) {
        List<Person> allPersons = listPersonsForForm(unitId);
        Map<Long, Person> personById =
                allPersons.stream().collect(Collectors.toMap(Person::getId, p -> p, (a, b) -> a, LinkedHashMap::new));
        List<Vehicle> unitVehicles = listVehiclesForForm(unitId);

        Map<Long, List<Long>> crewByVehicleId = new LinkedHashMap<>();
        Map<Long, Map<Long, IncidentVehicleCrewRole>> roleByVehicleAndPerson = new LinkedHashMap<>();
        for (Vehicle vehicle : unitVehicles) {
            crewByVehicleId.put(vehicle.getId(), new ArrayList<>());
        }
        List<Long> einsatzstelleCrewIds = new ArrayList<>();
        List<Long> wacheCrewIds = new ArrayList<>();

        Set<Long> diveraPersonIds = new HashSet<>();
        Set<Long> onVehiclePersonIds = new HashSet<>();
        Map<Long, IncidentPersonnelSource> sourceByPersonId = new LinkedHashMap<>();
        Map<Long, Boolean> paByPersonId = new LinkedHashMap<>();

        if (reportId != null) {
            for (IncidentReportPersonnel row : incidentReportPersonnelRepository.findByIncidentReportId(reportId)) {
                Person person = row.getPerson();
                if (person == null) {
                    continue;
                }
                sourceByPersonId.put(person.getId(), row.getSource());
                if (row.isUsesPa()) {
                    paByPersonId.put(person.getId(), true);
                }
                if (row.getSource() == IncidentPersonnelSource.DIVERA) {
                    diveraPersonIds.add(person.getId());
                }
                IncidentReportVehicle reportVehicle = row.getIncidentReportVehicle();
                if (reportVehicle == null) {
                    continue;
                }
                if (reportVehicle.getVehicle() != null) {
                    long vehicleId = reportVehicle.getVehicle().getId();
                    crewByVehicleId.computeIfAbsent(vehicleId, k -> new ArrayList<>()).add(person.getId());
                    if (row.getVehicleRole() != null) {
                        roleByVehicleAndPerson
                                .computeIfAbsent(vehicleId, k -> new LinkedHashMap<>())
                                .put(person.getId(), row.getVehicleRole());
                    }
                    onVehiclePersonIds.add(person.getId());
                } else if (reportVehicle.getVehicle() == null) {
                    String slotName = reportVehicle.getVehicleName();
                    if (IncidentCrewSupport.EINSATZSTELLE_VEHICLE_NAME.equals(slotName)) {
                        einsatzstelleCrewIds.add(person.getId());
                        onVehiclePersonIds.add(person.getId());
                    } else if (IncidentCrewSupport.WACHE_VEHICLE_NAME.equals(slotName)) {
                        wacheCrewIds.add(person.getId());
                        onVehiclePersonIds.add(person.getId());
                    }
                }
            }
        }

        Map<Long, Integer> sortOrderByPersonId = new LinkedHashMap<>();
        int sortIndex = 0;
        for (Person person : allPersons) {
            sortOrderByPersonId.put(person.getId(), sortIndex++);
        }

        List<KraefteFahrzeugeState.KraeftePersonView> manualPersons = new ArrayList<>();
        List<KraefteFahrzeugeState.KraeftePersonView> diveraPersons = new ArrayList<>();

        for (Person person : allPersons) {
            if (diveraPersonIds.contains(person.getId())) {
                if (!onVehiclePersonIds.contains(person.getId())) {
                    diveraPersons.add(toPersonView(person, sortOrderByPersonId, null, false, "divera"));
                }
            } else if (!onVehiclePersonIds.contains(person.getId())) {
                manualPersons.add(toPersonView(person, sortOrderByPersonId, null, false, "manual"));
            }
        }

        List<KraefteFahrzeugeState.KraefteVehicleView> vehicles = new ArrayList<>();
        for (Vehicle vehicle : unitVehicles) {
            List<Long> crewIds = crewByVehicleId.getOrDefault(vehicle.getId(), List.of());
            Map<Long, IncidentVehicleCrewRole> roles =
                    roleByVehicleAndPerson.getOrDefault(vehicle.getId(), Map.of());
            List<Person> crew = crewIds.stream()
                    .map(personById::get)
                    .filter(Objects::nonNull)
                    .toList();
            List<KraefteFahrzeugeState.KraeftePersonView> crewViews = crewIds.stream()
                    .map(personById::get)
                    .filter(Objects::nonNull)
                    .map(p -> toPersonView(
                            p,
                            sortOrderByPersonId,
                            roles.get(p.getId()),
                            paByPersonId.getOrDefault(p.getId(), false),
                            poolSourceFor(sourceByPersonId.get(p.getId()))))
                    .toList();
            Long einheitsfuehrerPersonId = null;
            Long maschinistPersonId = null;
            for (Map.Entry<Long, IncidentVehicleCrewRole> roleEntry : roles.entrySet()) {
                if (roleEntry.getValue() == IncidentVehicleCrewRole.EINHEITSFUEHRER) {
                    einheitsfuehrerPersonId = roleEntry.getKey();
                } else if (roleEntry.getValue() == IncidentVehicleCrewRole.MASCHINIST) {
                    maschinistPersonId = roleEntry.getKey();
                }
            }
            String typeKey = vehicle.getVehicleType();
            vehicles.add(new KraefteFahrzeugeState.KraefteVehicleView(
                    vehicle.getId(),
                    vehicle.getName(),
                    typeKey,
                    VehicleTypes.labelFor(typeKey),
                    new ArrayList<>(crewIds),
                    crewViews,
                    Besatzungsstaerke.format(crew),
                    einheitsfuehrerPersonId,
                    maschinistPersonId));
        }

        List<Person> einsatzstelleCrew = einsatzstelleCrewIds.stream()
                .map(personById::get)
                .filter(Objects::nonNull)
                .toList();
        KraefteFahrzeugeState.KraefteVehicleView einsatzstelle = buildVirtualSlotView(
                IncidentCrewSupport.EINSATZSTELLE_VEHICLE_ID,
                IncidentCrewSupport.EINSATZSTELLE_VEHICLE_NAME,
                einsatzstelleCrewIds,
                einsatzstelleCrew,
                sortOrderByPersonId,
                sourceByPersonId,
                paByPersonId);
        List<Person> wacheCrew = wacheCrewIds.stream()
                .map(personById::get)
                .filter(Objects::nonNull)
                .toList();
        KraefteFahrzeugeState.KraefteVehicleView wache = buildVirtualSlotView(
                IncidentCrewSupport.WACHE_VEHICLE_ID,
                IncidentCrewSupport.WACHE_VEHICLE_NAME,
                wacheCrewIds,
                wacheCrew,
                sortOrderByPersonId,
                sourceByPersonId,
                paByPersonId);

        return new KraefteFahrzeugeState(manualPersons, diveraPersons, einsatzstelle, wache, vehicles);
    }

    public String serializeKraefteFahrzeugeState(KraefteFahrzeugeState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            return "{}";
        }
    }

    public List<CrewAssignment> parseCrewAssignments(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<CrewAssignmentPayload> payloads = objectMapper.readValue(json, CREW_ASSIGNMENT_LIST);
            List<CrewAssignment> result = new ArrayList<>();
            for (CrewAssignmentPayload payload : payloads) {
                if (payload == null || payload.vehicleId() == null) {
                    continue;
                }
                List<Long> personIds = payload.personIds() != null
                        ? payload.personIds().stream().filter(Objects::nonNull).toList()
                        : List.of();
                List<Long> paPersonIds = payload.paPersonIds() != null
                        ? payload.paPersonIds().stream().filter(Objects::nonNull).toList()
                        : List.of();
                result.add(new CrewAssignment(
                        payload.vehicleId(),
                        personIds,
                        payload.einheitsfuehrerPersonId(),
                        payload.maschinistPersonId(),
                        paPersonIds));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    public IncidentReport requireReport(long unitId, long reportId) {
        return incidentReportRepository
                .findByIdAndUnitId(reportId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einsatzbericht nicht gefunden."));
    }

    public EinsatzberichtForm newForm(long unitId) {
        Unit unit = requireUnit(unitId);
        LocalDate today = LocalDate.now();
        UnitPostalCity.Parts address = UnitPostalCity.fromUnit(unit);
        EinsatzberichtForm form = new EinsatzberichtForm();
        form.setIncidentDate(today);
        form.setIncidentNumber(suggestIncidentNumber(unitId, today));
        form.setLocation(address.city());
        form.setPostalCode(address.postalCode());
        return form;
    }

    public String suggestIncidentNumber(long unitId, LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return resolveIncidentNumber(unitId, date);
    }

    private IncidentReport newDraft(long unitId) {
        IncidentReport report = new IncidentReport();
        report.setUnit(requireUnit(unitId));
        report.setIncidentDate(LocalDate.now());
        report.setIncidentTypeKey("SONSTIGES");
        report.setIncidentTypeLabel("Sonstiges");
        report.setTestData(testModeService.isEnabled());
        return report;
    }

    @Transactional
    public IncidentReport create(long unitId, EinsatzberichtFormData form, AppUserDetails actor) {
        validateRequired(form);
        IncidentReport report = newDraft(unitId);
        applyForm(report, form, unitId);
        report.setStatus(IncidentReportStatus.ENTWURF);
        report.setIncidentNumber(resolveIncidentNumber(unitId, form.incidentDate()));
        applyCreator(report, actor);
        IncidentReport saved = incidentReportRepository.save(report);
        saveCrewAssignments(saved, form, unitId);
        syncPaAtemschutzRecords(saved, form, actor);
        return saved;
    }

    @Transactional
    public void delete(long unitId, long reportId) {
        IncidentReport report = requireReport(unitId, reportId);
        long id = report.getId();
        atemschutzService.deleteIncidentPaFitnessRecords(id);
        incidentReportPersonnelRepository.deleteByIncidentReportId(id);
        incidentReportVehicleRepository.deleteByIncidentReportId(id);
        incidentReportRepository.delete(report);
    }

    @Transactional
    public void refreshDiveraFromLatestAlarmData(long unitId, long reportId) {
        IncidentReport report = requireReport(unitId, reportId);
        Long alarmId = report.getDiveraAlarmId();
        if (alarmId == null || alarmId <= 0) {
            return;
        }
        diveraService.findAlarmDetailsById(unitId, alarmId).ifPresent(details -> {
            applyDiveraAlarmDateTime(report, details);
            applyDiveraAddressFields(report, details);
            applyDiveraAlarmierungDurch(report, details, unitId);
            incidentReportRepository.save(report);
            refreshDiveraPersonnelFromDetails(unitId, details);
        });
    }

    @Transactional
    public void refreshDiveraPersonnelFromDetails(long unitId, DiveraAlarmDetails details) {
        if (details == null || details.alarmId() <= 0) {
            return;
        }
        UnitBerichteSettings settings = berichteSettingsService.ensureSettings(unitId);
        if (!settings.isImportPersonnelFromDivera()) {
            return;
        }
        incidentReportRepository
                .findByUnitIdAndDiveraAlarmId(unitId, details.alarmId())
                .ifPresent(report -> importDiveraPersonnel(report, details, unitId));
    }

    @Transactional
    public boolean createDraftFromDiveraIfMissing(long unitId, DiveraAlarmDetails details) {
        if (details == null || details.alarmId() <= 0) {
            return false;
        }
        if (incidentReportRepository.findByUnitIdAndDiveraAlarmId(unitId, details.alarmId()).isPresent()) {
            return false;
        }
        IncidentReport report = newDraft(unitId);
        applyDiveraDetails(report, details, unitId);
        report.setStatus(IncidentReportStatus.ENTWURF);
        report.setDiveraAlarmId(details.alarmId());
        report.setDiveraForeignId(details.externalId());
        report.setIncidentNumber(resolveIncidentNumber(unitId, report.getIncidentDate()));
        report.setCreatedByName("DIVERA");
        IncidentReport saved = incidentReportRepository.save(report);
        importDiveraPersonnel(saved, details, unitId);
        return true;
    }

    @Transactional
    public IncidentReport update(long unitId, long reportId, EinsatzberichtFormData form, AppUserDetails actor) {
        validateRequired(form);
        IncidentReport report = requireReport(unitId, reportId);
        applyForm(report, form, unitId);
        IncidentReport saved = incidentReportRepository.save(report);
        saveCrewAssignments(saved, form, unitId);
        syncPaAtemschutzRecords(saved, form, actor);
        return saved;
    }

    private void applyForm(IncidentReport report, EinsatzberichtFormData form, long unitId) {
        String stichwort = form.stichwort() != null ? form.stichwort().trim() : "";
        report.setIncidentDate(form.incidentDate());
        report.setAlarmTime(form.alarmTime());
        report.setDepartureTime(null);
        report.setArrivalTime(null);
        report.setEndTime(form.endTime());
        report.setStichwort(stichwort);
        report.setAlarmierungDurch(trimToNull(form.alarmierungDurch()));
        report.setIncidentTypeKey("SONSTIGES");
        report.setIncidentTypeLabel(stichwort.isEmpty() ? "Sonstiges" : stichwort);
        report.setSituation(trimToNull(form.nachrichtLeitstelle()));
        report.setLocation(form.location().trim());
        report.setPostalCode(trimToNull(form.postalCode()));
        report.setDistrict(trimToNull(form.district()));
        report.setStreet(trimToNull(form.street()));
        report.setHouseNumber(trimToNull(form.houseNumber()));
        report.setObjekt(trimToNull(form.objekt()));
        report.setEigentuemer(trimToNull(form.eigentuemer()));
        report.setExtinguishedBeforeArrival(form.extinguishedBeforeArrival());
        report.setMaliciousAlarm(form.maliciousAlarm());
        report.setFalseAlarm(form.falseAlarm());
        report.setSupraregional(form.supraregional());
        report.setBfInvolved(form.bfInvolved());
        report.setViolenceAgainstCrew(form.violenceAgainstCrew());
        report.setViolenceCount(Math.max(0, form.violenceCount()));
        applyCommander(report, form, unitId);
        report.setReporterName(trimToNull(form.reporterName()));
        report.setReporterPhone(trimToNull(form.reporterPhone()));
        int personnelCount = countAssignedPersons(form.crewAssignments());
        report.setStrengthLeadership(0);
        report.setStrengthSub(0);
        report.setStrengthCrew(personnelCount);
        report.setNotes(trimToNull(form.einsatzkurzbericht()));
        report.setPersonsRescued(Math.max(0, form.personsRescued()));
        report.setPersonsEvacuated(Math.max(0, form.personsEvacuated()));
        report.setPersonsInjured(Math.max(0, form.personsInjured()));
        report.setPersonsInjuredOwn(Math.max(0, form.personsInjuredOwn()));
        report.setPersonsRecovered(Math.max(0, form.personsRecovered()));
        report.setPersonsDead(Math.max(0, form.personsDead()));
        report.setPersonsDeadOwn(Math.max(0, form.personsDeadOwn()));
        report.setAnimalsRescued(Math.max(0, form.animalsRescued()));
        report.setAnimalsInjured(Math.max(0, form.animalsInjured()));
        report.setAnimalsRecovered(Math.max(0, form.animalsRecovered()));
        report.setAnimalsDead(Math.max(0, form.animalsDead()));
        report.setVehicleDamage(trimToNull(form.vehicleDamage()));
        report.setEquipmentDamage(trimToNull(form.equipmentDamage()));
    }

    private void applyCommander(IncidentReport report, EinsatzberichtFormData form, long unitId) {
        String commanderName = trimToNull(form.incidentCommander());
        report.setIncidentCommander(commanderName);
        report.setCommanderPerson(null);
        if (commanderName == null) {
            return;
        }
        listPersonsForForm(unitId).stream()
                .filter(p -> commanderName.equalsIgnoreCase(p.anwesenheitDisplayName()))
                .findFirst()
                .ifPresent(report::setCommanderPerson);
    }

    private void saveCrewAssignments(IncidentReport report, EinsatzberichtFormData form, long unitId) {
        long reportId = report.getId();
        Map<Long, IncidentPersonnelSource> existingSources = loadExistingSources(reportId);
        incidentReportPersonnelRepository.deleteByIncidentReportId(reportId);
        incidentReportVehicleRepository.deleteByIncidentReportId(reportId);

        Map<Long, IncidentReportVehicle> reportVehicleByUnitVehicleId = new HashMap<>();
        for (Vehicle vehicle : listVehiclesForForm(unitId)) {
            IncidentReportVehicle row = new IncidentReportVehicle();
            row.setIncidentReport(report);
            row.setVehicle(vehicle);
            row.setVehicleName(vehicle.getName());
            IncidentReportVehicle saved = incidentReportVehicleRepository.save(row);
            reportVehicleByUnitVehicleId.put(vehicle.getId(), saved);
        }

        Set<Long> assignedPersons = new HashSet<>();
        List<CrewAssignment> assignments =
                form.crewAssignments() != null ? form.crewAssignments() : List.of();

        Map<Long, IncidentReportVehicle> virtualVehicles = new HashMap<>();

        for (CrewAssignment assignment : assignments) {
            IncidentReportVehicle reportVehicle;
            if (IncidentCrewSupport.isVirtualSlot(assignment.vehicleId())) {
                reportVehicle = virtualVehicles.computeIfAbsent(assignment.vehicleId(), slotId -> {
                    IncidentReportVehicle row = new IncidentReportVehicle();
                    row.setIncidentReport(report);
                    row.setVehicle(null);
                    row.setVehicleName(IncidentCrewSupport.virtualSlotName(slotId));
                    return incidentReportVehicleRepository.save(row);
                });
            } else {
                reportVehicle = reportVehicleByUnitVehicleId.get(assignment.vehicleId());
            }
            if (reportVehicle == null) {
                continue;
            }
            Long einheitsfuehrerPersonId =
                    assignment.vehicleId() > 0 ? assignment.einheitsfuehrerPersonId() : null;
            Long maschinistPersonId = assignment.vehicleId() > 0 ? assignment.maschinistPersonId() : null;
            Set<Long> paPersonIds = assignment.paPersonIds() != null
                    ? assignment.paPersonIds().stream().filter(Objects::nonNull).collect(Collectors.toSet())
                    : Set.of();
            for (Long personId : assignment.personIds()) {
                if (personId == null || !assignedPersons.add(personId)) {
                    continue;
                }
                Person person = resolvePersonForUnit(personId, unitId);
                IncidentReportPersonnel row = new IncidentReportPersonnel();
                row.setIncidentReport(report);
                row.setPerson(person);
                row.setIncidentReportVehicle(reportVehicle);
                row.setDisplayName(person.anwesenheitDisplayName());
                row.setSource(existingSources.getOrDefault(personId, IncidentPersonnelSource.MANUAL));
                if (personId.equals(einheitsfuehrerPersonId)) {
                    row.setVehicleRole(IncidentVehicleCrewRole.EINHEITSFUEHRER);
                } else if (personId.equals(maschinistPersonId)) {
                    row.setVehicleRole(IncidentVehicleCrewRole.MASCHINIST);
                }
                row.setUsesPa(paPersonIds.contains(personId));
                incidentReportPersonnelRepository.save(row);
            }
        }
    }

    private void syncPaAtemschutzRecords(IncidentReport report, EinsatzberichtFormData form, AppUserDetails actor) {
        Set<Long> paPersonIds = collectPaPersonIds(form.crewAssignments());
        Long userId = actor != null ? actor.getUserId() : null;
        atemschutzService.syncIncidentPaFitnessRecords(
                report.getUnit().getId(),
                report.getId(),
                paPersonIds,
                report.getIncidentDate(),
                incidentPaSourceLabel(report),
                userId);
    }

    private static Set<Long> collectPaPersonIds(List<CrewAssignment> assignments) {
        Set<Long> result = new LinkedHashSet<>();
        if (assignments == null) {
            return result;
        }
        for (CrewAssignment assignment : assignments) {
            if (assignment.paPersonIds() == null) {
                continue;
            }
            assignment.paPersonIds().stream().filter(Objects::nonNull).forEach(result::add);
        }
        return result;
    }

    private static String incidentPaSourceLabel(IncidentReport report) {
        String number = report.getIncidentNumber() != null ? report.getIncidentNumber().trim() : "";
        String stichwort = report.getStichwort() != null ? report.getStichwort().trim() : "";
        if (!number.isEmpty() && !stichwort.isEmpty()) {
            return number + " " + stichwort;
        }
        if (!number.isEmpty()) {
            return number;
        }
        if (!stichwort.isEmpty()) {
            return stichwort;
        }
        return "Einsatzbericht";
    }

    private Map<Long, IncidentPersonnelSource> loadExistingSources(long reportId) {
        Map<Long, IncidentPersonnelSource> sources = new HashMap<>();
        for (IncidentReportPersonnel row : incidentReportPersonnelRepository.findByIncidentReportId(reportId)) {
            if (row.getPerson() != null) {
                sources.put(row.getPerson().getId(), row.getSource());
            }
        }
        return sources;
    }

    private int countAssignedPersons(List<CrewAssignment> assignments) {
        if (assignments == null) {
            return 0;
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (CrewAssignment assignment : assignments) {
            if (assignment.personIds() != null) {
                assignment.personIds().stream().filter(Objects::nonNull).forEach(ids::add);
            }
        }
        return ids.size();
    }

    private KraefteFahrzeugeState.KraefteVehicleView buildVirtualSlotView(
            long slotId,
            String slotName,
            List<Long> crewIds,
            List<Person> crew,
            Map<Long, Integer> sortOrderByPersonId,
            Map<Long, IncidentPersonnelSource> sourceByPersonId,
            Map<Long, Boolean> paByPersonId) {
        return new KraefteFahrzeugeState.KraefteVehicleView(
                slotId,
                slotName,
                null,
                null,
                new ArrayList<>(crewIds),
                crew.stream()
                        .map(p -> toPersonView(
                                p,
                                sortOrderByPersonId,
                                null,
                                paByPersonId.getOrDefault(p.getId(), false),
                                poolSourceFor(sourceByPersonId.get(p.getId()))))
                        .toList(),
                Besatzungsstaerke.format(crew),
                null,
                null);
    }

    private static String poolSourceFor(IncidentPersonnelSource source) {
        return source == IncidentPersonnelSource.DIVERA ? "divera" : "manual";
    }

    private KraefteFahrzeugeState.KraeftePersonView toPersonView(
            Person person,
            Map<Long, Integer> sortOrderByPersonId,
            IncidentVehicleCrewRole vehicleRole,
            boolean usesPa,
            String poolSource) {
        return new KraefteFahrzeugeState.KraeftePersonView(
                person.getId(),
                person.anwesenheitDisplayName(),
                Besatzungsstaerke.qualTier(person).name(),
                sortOrderByPersonId.getOrDefault(person.getId(), 0),
                vehicleRole != null ? vehicleRole.name() : null,
                usesPa,
                poolSource);
    }

    private Person resolvePersonForUnit(long personId, long unitId) {
        Person person = personalService.requirePerson(personId);
        if (person.getUnit().getId() != unitId) {
            throw new IllegalArgumentException("Person gehört nicht zu dieser Einheit.");
        }
        return person;
    }

    private void applyCreator(IncidentReport report, AppUserDetails actor) {
        if (actor == null) {
            return;
        }
        User user = userRepository.findById(actor.getUserId()).orElse(null);
        report.setCreatedByUser(user);
        report.setCreatedByName(actor.getDisplayName());
    }

    private String resolveIncidentNumber(long unitId, LocalDate date) {
        String datePrefix = date + "-";
        int next = 1;
        Optional<String> max = incidentReportRepository.findMaxIncidentNumberForDate(unitId, datePrefix);
        if (max.isPresent()) {
            String number = max.get();
            if (number.startsWith(datePrefix)) {
                String suffix = number.substring(datePrefix.length());
                try {
                    next = Integer.parseInt(suffix) + 1;
                } catch (NumberFormatException ignored) {
                    next = 1;
                }
            }
        }
        return datePrefix + String.format("%02d", next);
    }

    private void applyDiveraAlarmierungDurch(IncidentReport report, DiveraAlarmDetails details, long unitId) {
        String alarmierung = diveraMappingService.formatAlarmierungDurch(unitId, details.groupIds());
        if (alarmierung != null && !alarmierung.isBlank()) {
            report.setAlarmierungDurch(alarmierung);
        }
    }

    private void applyDiveraAddressFields(IncidentReport report, DiveraAlarmDetails details) {
        if (details.city() != null && !details.city().isBlank()) {
            report.setLocation(details.city());
        }
        if (details.postalCode() != null && !details.postalCode().isBlank()) {
            report.setPostalCode(details.postalCode());
        }
        if (details.district() != null && !details.district().isBlank()) {
            report.setDistrict(details.district());
        }
        if (details.street() != null && !details.street().isBlank()) {
            report.setStreet(details.street());
        }
        if (details.houseNumber() != null && !details.houseNumber().isBlank()) {
            report.setHouseNumber(details.houseNumber());
        }
    }

    private void applyDiveraDetails(IncidentReport report, DiveraAlarmDetails details, long unitId) {
        applyDiveraAlarmDateTime(report, details);
        if (report.getIncidentDate() == null) {
            report.setIncidentDate(LocalDate.now(DIVERA_ZONE));
        }
        report.setDepartureTime(toLocalTime(details.tsDepartureSeconds()));
        report.setArrivalTime(toLocalTime(details.tsArrivalSeconds()));
        report.setEndTime(null);

        String stichwort = firstNonBlank(details.title(), "DIVERA-Einsatz");
        report.setStichwort(stichwort);
        report.setIncidentTypeKey("SONSTIGES");
        report.setIncidentTypeLabel(stichwort);
        applyDiveraAlarmierungDurch(report, details, unitId);

        String location = firstNonBlank(details.city(), details.address(), "DIVERA-Einsatz");
        report.setLocation(location);
        report.setPostalCode(details.postalCode());
        report.setDistrict(details.district());
        report.setStreet(details.street());
        report.setHouseNumber(details.houseNumber());
        report.setObjekt(details.objekt());
        report.setEigentuemer(details.eigentuemer());
        report.setReporterName(details.reporterName());
        report.setReporterPhone(details.reporterPhone());
        report.setFireObject(details.fireObject());
        report.setSituation(firstNonBlank(details.text(), details.situation()));
        report.setMeasures(details.measures());
        report.setFalseAlarm(details.falseAlarm());
        report.setMaliciousAlarm(details.maliciousAlarm());

        report.setNotes(null);

        report.setResourcesJson(writeDiveraResources(details));

        Unit unit = requireUnit(unitId);
        UnitPostalCity.Parts address = UnitPostalCity.fromUnit(unit);
        if (report.getPostalCode() == null || report.getPostalCode().isBlank()) {
            report.setPostalCode(address.postalCode());
        }
        if ("DIVERA-Einsatz".equals(report.getLocation()) && address.city() != null && !address.city().isBlank()) {
            report.setLocation(address.city());
        }
    }

    private void importDiveraPersonnel(IncidentReport report, DiveraAlarmDetails details, long unitId) {
        UnitBerichteSettings settings = berichteSettingsService.ensureSettings(unitId);
        if (!settings.isImportPersonnelFromDivera()) {
            return;
        }
        List<String> allowedStatusIds = berichteSettingsService.parsePersonnelStatusIds(settings);
        Map<Long, Integer> statusByUcr = loadDiveraUserStatuses(unitId);
        Set<Long> targetUcrIds = resolveTargetUcrIds(details, allowedStatusIds, statusByUcr);
        if (targetUcrIds.isEmpty()) {
            log.debug(
                    "[Divera→Personal] unit={} alarm={} report={} — keine UCR-IDs "
                            + "(ucr_answered leer oder keine passende Status-ID? hits={})",
                    unitId,
                    details.alarmId(),
                    report.getId(),
                    details.answeredHits() != null ? details.answeredHits().size() : 0);
            return;
        }
        boolean testData = testModeService.isEnabled();
        Set<Long> alreadyPresent = incidentReportPersonnelRepository.findByIncidentReportId(report.getId()).stream()
                .map(IncidentReportPersonnel::getPerson)
                .filter(Objects::nonNull)
                .map(Person::getId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> assigned = new HashSet<>(alreadyPresent);
        int added = 0;
        int unmatched = 0;
        for (Long ucrId : targetUcrIds) {
            Optional<Person> personOpt = resolvePersonForDiveraUcr(unitId, ucrId, testData);
            if (personOpt.isEmpty()) {
                unmatched++;
                continue;
            }
            Person person = personOpt.get();
            if (!assigned.add(person.getId())) {
                continue;
            }
            IncidentReportPersonnel row = new IncidentReportPersonnel();
            row.setIncidentReport(report);
            row.setPerson(person);
            row.setIncidentReportVehicle(null);
            row.setDisplayName(person.anwesenheitDisplayName());
            row.setSource(IncidentPersonnelSource.DIVERA);
            incidentReportPersonnelRepository.save(row);
            added++;
        }
        report.setStrengthCrew(assigned.size());
        if (added > 0 || unmatched > 0) {
            log.info(
                    "[Divera→Personal] unit={} alarm={} report={} ucr={} neu={} ohneZuordnung={}",
                    unitId,
                    details.alarmId(),
                    report.getId(),
                    targetUcrIds.size(),
                    added,
                    unmatched);
        }
    }

    private Set<Long> resolveTargetUcrIds(
            DiveraAlarmDetails details, List<String> allowedStatusIds, Map<Long, Integer> statusByUcr) {
        Set<Long> fromAnswered = new LinkedHashSet<>();
        if (details.answeredHits() != null) {
            for (var hit : details.answeredHits()) {
                if (hit.ucrId() == null || hit.ucrId().isBlank()) {
                    continue;
                }
                if (!allowedStatusIds.isEmpty()) {
                    String statusId = hit.statusId() != null ? hit.statusId().trim() : "";
                    if (statusId.isEmpty() || !allowedStatusIds.contains(statusId)) {
                        continue;
                    }
                }
                try {
                    long ucrId = Long.parseLong(hit.ucrId().trim());
                    if (ucrId > 0) {
                        fromAnswered.add(ucrId);
                    }
                } catch (NumberFormatException ignored) {
                    // ignore malformed UCR
                }
            }
        }
        if (!fromAnswered.isEmpty()) {
            return fromAnswered;
        }
        if (details.answeredUcrIds() != null) {
            for (Long ucrId : details.answeredUcrIds()) {
                if (ucrId != null && ucrId > 0) {
                    fromAnswered.add(ucrId);
                }
            }
        }
        if (!fromAnswered.isEmpty()) {
            return fromAnswered;
        }
        if (allowedStatusIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        for (Map.Entry<Long, Integer> entry : statusByUcr.entrySet()) {
            if (allowedStatusIds.contains(String.valueOf(entry.getValue()))) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private Map<Long, Integer> loadDiveraUserStatuses(long unitId) {
        return diveraSettingsRepository
                .findByUnitId(unitId)
                .map(cfg -> diveraApiClient.fetchUserStatusByUcr(
                        cfg.getApiBaseUrl() != null ? cfg.getApiBaseUrl() : DiveraIntegrationSupport.DEFAULT_API_BASE,
                        cfg.getAccessKey()))
                .orElse(Map.of());
    }

    private Optional<Person> resolvePersonForDiveraUcr(long unitId, long ucrId, boolean testData) {
        String ucr = String.valueOf(ucrId);
        Optional<Person> direct = personRepository.findByUnitIdAndDiveraUcrId(unitId, ucr, testData);
        if (direct.isPresent()) {
            return direct;
        }
        return personalService.listPersons(unitId).stream()
                .filter(p -> ucr.equals(p.getDiveraUcrId()))
                .findFirst();
    }

    private String writeDiveraResources(DiveraAlarmDetails details) {
        try {
            Map<String, Object> divera = new LinkedHashMap<>();
            divera.put("alarmId", details.alarmId());
            divera.put("externalId", details.externalId());
            divera.put("closed", details.closed());
            divera.put("dateEpochSeconds", details.dateEpochSeconds());
            divera.put("tsCreateSeconds", details.tsCreateSeconds());
            divera.put("tsCloseSeconds", details.tsCloseSeconds());
            return objectMapper.writeValueAsString(Map.of("divera", divera));
        } catch (Exception e) {
            return "{}";
        }
    }

    private static void applyDiveraAlarmDateTime(IncidentReport report, DiveraAlarmDetails details) {
        long alarmEpoch = resolveAlarmEpochSeconds(details);
        if (alarmEpoch <= 0) {
            return;
        }
        var zoned = Instant.ofEpochSecond(alarmEpoch).atZone(DIVERA_ZONE);
        report.setIncidentDate(zoned.toLocalDate());
        report.setAlarmTime(zoned.toLocalTime());
    }

    private static long resolveAlarmEpochSeconds(DiveraAlarmDetails details) {
        long dateEpoch = details.dateEpochSeconds();
        long createEpoch = details.tsCreateSeconds();
        return dateEpoch > 0 ? dateEpoch : createEpoch;
    }

    private static LocalTime toLocalTime(long epochSeconds) {
        if (epochSeconds <= 0) {
            return null;
        }
        return Instant.ofEpochSecond(epochSeconds).atZone(DIVERA_ZONE).toLocalTime();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private void validateRequired(EinsatzberichtFormData form) {
        if (form.incidentDate() == null) {
            throw new IllegalArgumentException("Datum ist Pflichtfeld.");
        }
        if (form.location() == null || form.location().isBlank()) {
            throw new IllegalArgumentException("Einsatzort ist Pflichtfeld.");
        }
        if (form.stichwort() == null || form.stichwort().isBlank()) {
            throw new IllegalArgumentException("Stichwort ist Pflichtfeld.");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Unit requireUnit(long unitId) {
        return unitRepository.findById(unitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
    }

    private record CrewAssignmentPayload(
            Long vehicleId,
            List<Long> personIds,
            Long einheitsfuehrerPersonId,
            Long maschinistPersonId,
            List<Long> paPersonIds) {}
}
