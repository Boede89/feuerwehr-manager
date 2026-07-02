package de.feuerwehr.manager.berichte;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.divera.DiveraAlarmDetailsMapper;
import de.feuerwehr.manager.divera.DiveraAlarmDetailsMapper.DiveraAlarmDetails;
import de.feuerwehr.manager.divera.DiveraApiClient;
import static de.feuerwehr.manager.divera.DiveraIntegrationSupport.DIVERA_ZONE;

import de.feuerwehr.manager.divera.DiveraIntegrationSupport;
import de.feuerwehr.manager.divera.DiveraUserDirectory;
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
import de.feuerwehr.manager.technik.VehicleEquipment;
import de.feuerwehr.manager.technik.VehicleEquipmentRepository;
import de.feuerwehr.manager.technik.VehicleRepository;
import de.feuerwehr.manager.technik.VehicleTypes;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitAdminService;
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
    private static final TypeReference<List<DeployedEquipmentPayload>> DEPLOYED_EQUIPMENT_LIST =
            new TypeReference<>() {};

    private final IncidentReportRepository incidentReportRepository;
    private final IncidentReportPersonnelRepository incidentReportPersonnelRepository;
    private final IncidentReportVehicleRepository incidentReportVehicleRepository;
    private final IncidentReportEquipmentRepository incidentReportEquipmentRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final PersonalService personalService;
    private final PersonRepository personRepository;
    private final UnitAdminService unitAdminService;
    private final VehicleRepository vehicleRepository;
    private final VehicleEquipmentRepository vehicleEquipmentRepository;
    private final TestModeService testModeService;
    private final BerichteSettingsService berichteSettingsService;
    private final DiveraApiClient diveraApiClient;
    private final DiveraService diveraService;
    private final DiveraMappingService diveraMappingService;
    private final AtemschutzService atemschutzService;
    private final EinsatzberichtAttachmentService einsatzberichtAttachmentService;
    private final UnitDiveraSettingsRepository diveraSettingsRepository;
    private final IncidentReportChangeRepository incidentReportChangeRepository;
    private final ObjectMapper objectMapper;

    public List<IncidentReport> listByUnit(long unitId) {
        return incidentReportRepository.findByUnitIdOrderByDateDesc(unitId, includeTestReports());
    }

    public List<IncidentReport> listFiltered(long unitId, int year, String stichwort, IncidentReportStatus status) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = yearStart.plusYears(1);
        String stichwortFilter = stichwort != null ? stichwort.trim() : "";
        return incidentReportRepository.findFilteredByUnit(
                unitId, yearStart, yearEnd, stichwortFilter, status, includeTestReports());
    }

    public List<Integer> listFilterYears() {
        int currentYear = LocalDate.now().getYear();
        List<Integer> years = new ArrayList<>();
        for (int y = currentYear; y >= currentYear - 10; y--) {
            years.add(y);
        }
        return years;
    }

    @Transactional(readOnly = true)
    public EinsatzberichtListResponse listForYear(long unitId, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = yearStart.plusYears(1);
        List<IncidentReport> reports =
                incidentReportRepository.findByUnitIdAndYear(unitId, yearStart, yearEnd, includeTestReports());
        List<EinsatzberichtListItemView> items = reports.stream().map(this::toListItem).toList();
        LinkedHashSet<String> stichworte = new LinkedHashSet<>();
        for (IncidentReport report : reports) {
            String stichwort = displayStichwort(report);
            if (!stichwort.isBlank()) {
                stichworte.add(stichwort);
            }
        }
        return new EinsatzberichtListResponse(items, List.copyOf(stichworte));
    }

    @Transactional(readOnly = true)
    public List<IncidentReportChange> listChanges(long unitId, long reportId) {
        requireReport(unitId, reportId);
        return incidentReportChangeRepository.findByReportIdWithFields(reportId);
    }

    @Transactional(readOnly = true)
    public List<Person> listPersonsForForm(long unitId) {
        return personalService.listPersons(unitId);
    }

    public List<Vehicle> listVehiclesForForm(long unitId) {
        return unitAdminService.listVehicles(unitId);
    }

    public String serializeUnitVehiclesJson(long unitId) {
        try {
            List<Map<String, Object>> items = listVehiclesForForm(unitId).stream()
                    .map(vehicle -> Map.<String, Object>of("id", vehicle.getId(), "name", vehicle.getName()))
                    .toList();
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
    }

    public List<String> listKnownStichworte(long unitId) {
        return incidentReportRepository.findDistinctStichworteByUnitId(unitId, includeTestReports());
    }

    @Transactional(readOnly = true)
    public boolean isForeignUnitPersonnelAllowed(long unitId) {
        return berichteSettingsService.isForeignUnitPersonnelAllowed(unitId);
    }

    @Transactional(readOnly = true)
    public List<ForeignUnitOption> listForeignUnits(long reportUnitId) {
        if (!berichteSettingsService.isForeignUnitPersonnelAllowed(reportUnitId)) {
            return List.of();
        }
        return unitRepository.findActiveVisible(testModeService.isEnabled()).stream()
                .filter(unit -> unit.getId() != reportUnitId)
                .map(unit -> new ForeignUnitOption(unit.getId(), unit.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ForeignPersonOption> listForeignPersonnel(long reportUnitId, long sourceUnitId, String query) {
        if (!berichteSettingsService.isForeignUnitPersonnelAllowed(reportUnitId)) {
            return List.of();
        }
        Unit sourceUnit = unitRepository
                .findById(sourceUnitId)
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        String unitName = sourceUnit.getName();
        String normalized = query != null ? query.trim() : "";
        List<Person> persons;
        if (normalized.length() < 2) {
            persons = personRepository.findActiveByUnitIdWithUnit(sourceUnitId, includeTestReports());
        } else {
            persons = personRepository.searchActiveByUnitId(sourceUnitId, normalized, includeTestReports());
        }
        return persons.stream()
                .map(person -> new ForeignPersonOption(
                        person.getId(),
                        person.anwesenheitDisplayName(),
                        Besatzungsstaerke.qualTier(person).name(),
                        sourceUnitId,
                        unitName))
                .toList();
    }

    @Transactional(readOnly = true)
    public KraefteFahrzeugeState buildKraefteFahrzeugeState(long unitId, Long reportId) {
        return buildKraefteFahrzeugeState(unitId, reportId, null, null, null);
    }

    /**
     * Kräfte-Board für Anwesenheitslisten: Termin-Zielgruppe im Reserve-Pool links,
     * explizit gespeicherte Anwesenheit im Slot „Anwesend“.
     */
    @Transactional(readOnly = true)
    public KraefteFahrzeugeState buildKraefteFahrzeugeStateForAnwesenheit(
            long unitId, List<Long> anwesendPersonIds, Set<Long> manualPoolPersonIds) {
        return buildKraefteFahrzeugeState(unitId, null, anwesendPersonIds, manualPoolPersonIds, null);
    }

    /** Kräfte-Board aus gespeicherter Crew-JSON (Fahrzeuge, Anwesend, Rollen). */
    @Transactional(readOnly = true)
    public KraefteFahrzeugeState buildKraefteFahrzeugeStateForAnwesenheitWithAssignments(
            long unitId, List<CrewAssignment> crewAssignments, Set<Long> manualPoolPersonIds) {
        return buildKraefteFahrzeugeState(unitId, null, null, manualPoolPersonIds, crewAssignments);
    }

    private KraefteFahrzeugeState buildKraefteFahrzeugeState(
            long unitId,
            Long reportId,
            List<Long> presetAnwesendPersonIds,
            Set<Long> manualPoolPersonIds,
            List<CrewAssignment> presetCrewAssignments) {
        List<Person> allPersons = listPersonsForForm(unitId);
        List<IncidentReportPersonnel> reportRows =
                reportId != null ? incidentReportPersonnelRepository.findByIncidentReportId(reportId) : List.of();
        Set<Long> extraPersonIds = new LinkedHashSet<>();
        for (IncidentReportPersonnel row : reportRows) {
            if (row.getPerson() != null) {
                extraPersonIds.add(row.getPerson().getId());
            }
        }
        Map<Long, Person> personById = new LinkedHashMap<>();
        allPersons.forEach(person -> personById.put(person.getId(), person));
        if (!extraPersonIds.isEmpty()) {
            personRepository.findActiveByIdIn(extraPersonIds, includeTestReports()).forEach(person -> personById.putIfAbsent(person.getId(), person));
        }
        List<Vehicle> unitVehicles = listVehiclesForForm(unitId);

        Map<Long, List<Long>> crewByVehicleId = new LinkedHashMap<>();
        Map<Long, Map<Long, IncidentVehicleCrewRole>> roleByVehicleAndPerson = new LinkedHashMap<>();
        for (Vehicle vehicle : unitVehicles) {
            crewByVehicleId.put(vehicle.getId(), new ArrayList<>());
        }
        List<Long> beteiligtCrewIds = new ArrayList<>();
        List<Long> einsatzstelleCrewIds = new ArrayList<>();
        List<Long> wacheCrewIds = new ArrayList<>();
        Map<Long, Boolean> involvedByVehicleId = new LinkedHashMap<>();

        Set<Long> diveraPersonIds = new HashSet<>();
        Set<Long> onVehicleRefIds = new HashSet<>();
        Map<Long, IncidentPersonnelSource> sourceByRefId = new LinkedHashMap<>();
        Map<Long, Boolean> paByRefId = new LinkedHashMap<>();
        Map<Long, IncidentReportPersonnel> rowByRefId = new LinkedHashMap<>();
        Map<Long, String> diveraUcrReserveNames = new LinkedHashMap<>();

        if (reportId != null) {
            for (IncidentReportVehicle reportVehicle :
                    incidentReportVehicleRepository.findByIncidentReportId(reportId)) {
                if (reportVehicle.getVehicle() != null) {
                    involvedByVehicleId.put(reportVehicle.getVehicle().getId(), reportVehicle.isInvolved());
                }
            }
            for (IncidentReportPersonnel row : reportRows) {
                long refId;
                try {
                    refId = personnelRefId(row);
                } catch (IllegalStateException ex) {
                    log.warn(
                            "Personaleintrag {} in Bericht {} übersprungen: {}",
                            row.getId(),
                            reportId,
                            ex.getMessage());
                    continue;
                }
                rowByRefId.put(refId, row);
                sourceByRefId.put(refId, row.getSource());
                if (row.isUsesPa()) {
                    paByRefId.put(refId, true);
                }
                if (row.getPerson() != null && row.getSource() == IncidentPersonnelSource.DIVERA) {
                    diveraPersonIds.add(row.getPerson().getId());
                } else if (row.getDiveraUcrId() != null && !row.getDiveraUcrId().isBlank()) {
                    try {
                        long ucrId = Long.parseLong(row.getDiveraUcrId().trim());
                        diveraUcrReserveNames.put(ucrId, row.getDisplayName());
                    } catch (NumberFormatException ignored) {
                        // ignore malformed UCR
                    }
                }
                IncidentReportVehicle reportVehicle = row.getIncidentReportVehicle();
                if (reportVehicle == null) {
                    if (row.getSource() == IncidentPersonnelSource.FOREIGN
                            && row.getPerson() != null
                            && !onVehicleRefIds.contains(refId)) {
                        beteiligtCrewIds.add(refId);
                        onVehicleRefIds.add(refId);
                    }
                    continue;
                }
                if (reportVehicle.getVehicle() != null) {
                    long vehicleId = reportVehicle.getVehicle().getId();
                    crewByVehicleId.computeIfAbsent(vehicleId, k -> new ArrayList<>()).add(refId);
                    if (row.getVehicleRole() != null) {
                        roleByVehicleAndPerson
                                .computeIfAbsent(vehicleId, k -> new LinkedHashMap<>())
                                .put(refId, row.getVehicleRole());
                    }
                    onVehicleRefIds.add(refId);
                } else {
                    String slotName = reportVehicle.getVehicleName();
                    if (IncidentCrewSupport.BETEILIGT_VEHICLE_NAME.equals(slotName)) {
                        beteiligtCrewIds.add(refId);
                        onVehicleRefIds.add(refId);
                    } else if (IncidentCrewSupport.EINSATZSTELLE_VEHICLE_NAME.equals(slotName)) {
                        einsatzstelleCrewIds.add(refId);
                        onVehicleRefIds.add(refId);
                    } else if (IncidentCrewSupport.WACHE_VEHICLE_NAME.equals(slotName)) {
                        wacheCrewIds.add(refId);
                        onVehicleRefIds.add(refId);
                    }
                }
            }
            incidentReportRepository
                    .findByIdAndUnitId(reportId, unitId, includeTestReports())
                    .ifPresent(report -> {
                Person commander = report.getCommanderPerson();
                if (commander == null) {
                    return;
                }
                long commanderId = commander.getId();
                personRepository
                        .findActiveByIdIn(List.of(commanderId), includeTestReports())
                        .forEach(person -> personById.putIfAbsent(person.getId(), person));
                if (!onVehicleRefIds.contains(commanderId)) {
                    if (!beteiligtCrewIds.contains(commanderId)) {
                        beteiligtCrewIds.add(commanderId);
                    }
                    onVehicleRefIds.add(commanderId);
                }
            });
        }

        if (presetCrewAssignments != null && !presetCrewAssignments.isEmpty()) {
            applyPresetCrewAssignments(
                    presetCrewAssignments,
                    personById,
                    crewByVehicleId,
                    roleByVehicleAndPerson,
                    beteiligtCrewIds,
                    einsatzstelleCrewIds,
                    wacheCrewIds,
                    involvedByVehicleId,
                    onVehicleRefIds,
                    paByRefId);
        } else if (presetAnwesendPersonIds != null && !presetAnwesendPersonIds.isEmpty()) {
            LinkedHashSet<Long> presetIds = new LinkedHashSet<>();
            for (Long pid : presetAnwesendPersonIds) {
                if (pid != null) {
                    presetIds.add(pid);
                }
            }
            if (!presetIds.isEmpty()) {
                personRepository
                        .findActiveByIdIn(presetIds, includeTestReports())
                        .forEach(person -> personById.putIfAbsent(person.getId(), person));
                for (Long pid : presetAnwesendPersonIds) {
                    if (pid == null || onVehicleRefIds.contains(pid)) {
                        continue;
                    }
                    beteiligtCrewIds.add(pid);
                    onVehicleRefIds.add(pid);
                }
            }
        }

        Map<Long, Integer> sortOrderByRefId = new LinkedHashMap<>();
        int sortIndex = 0;
        for (Person person : allPersons) {
            sortOrderByRefId.put(person.getId(), sortIndex++);
        }
        for (Long ucrId : diveraUcrReserveNames.keySet()) {
            sortOrderByRefId.putIfAbsent(IncidentPersonnelRefs.refFromUcr(ucrId), sortIndex++);
        }

        List<KraefteFahrzeugeState.KraeftePersonView> manualPersons = new ArrayList<>();
        List<KraefteFahrzeugeState.KraeftePersonView> diveraPersons = new ArrayList<>();
        List<KraefteFahrzeugeState.KraeftePersonView> foreignPersons = new ArrayList<>();

        if (manualPoolPersonIds != null && !manualPoolPersonIds.isEmpty()) {
            personRepository
                    .findActiveByIdIn(manualPoolPersonIds, includeTestReports())
                    .forEach(person -> personById.putIfAbsent(person.getId(), person));
        }
        for (Person person : allPersons) {
            if (diveraPersonIds.contains(person.getId())) {
                if (!onVehicleRefIds.contains(person.getId())) {
                    diveraPersons.add(toPersonView(
                            person, sortOrderByRefId, null, false, "divera", unitLabelForPerson(person, unitId)));
                }
            } else if (!onVehicleRefIds.contains(person.getId())) {
                if (manualPoolPersonIds != null
                        && !manualPoolPersonIds.isEmpty()
                        && !manualPoolPersonIds.contains(person.getId())) {
                    continue;
                }
                manualPersons.add(toPersonView(person, sortOrderByRefId, null, false, "manual", null));
            }
        }
        for (Map.Entry<Long, String> ucrEntry : diveraUcrReserveNames.entrySet()) {
            long refId = IncidentPersonnelRefs.refFromUcr(ucrEntry.getKey());
            if (!onVehicleRefIds.contains(refId)) {
                diveraPersons.add(toUcrPersonView(
                        refId, ucrEntry.getKey(), ucrEntry.getValue(), sortOrderByRefId.getOrDefault(refId, 0)));
            }
        }
        List<KraefteFahrzeugeState.KraefteVehicleView> vehicles = new ArrayList<>();
        for (Vehicle vehicle : unitVehicles) {
            List<Long> crewRefIds = crewByVehicleId.getOrDefault(vehicle.getId(), List.of());
            Map<Long, IncidentVehicleCrewRole> roles =
                    roleByVehicleAndPerson.getOrDefault(vehicle.getId(), Map.of());
            List<Person> crewPersons = crewRefIds.stream()
                    .map(personById::get)
                    .filter(Objects::nonNull)
                    .toList();
            List<KraefteFahrzeugeState.KraeftePersonView> crewViews = crewRefIds.stream()
                    .map(refId -> toPersonViewFromRef(
                            refId,
                            personById,
                            rowByRefId,
                            sortOrderByRefId,
                            roles.get(refId),
                            paByRefId.getOrDefault(refId, false),
                            sourceByRefId.get(refId),
                            unitId))
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
            boolean hasCrew = !crewRefIds.isEmpty();
            boolean involvedFromDb = involvedByVehicleId.getOrDefault(vehicle.getId(), false);
            boolean involvedInIncident = involvedFromDb || hasCrew;
            boolean manuallyInvolvedInIncident = involvedFromDb && !hasCrew;
            vehicles.add(new KraefteFahrzeugeState.KraefteVehicleView(
                    vehicle.getId(),
                    vehicle.getName(),
                    typeKey,
                    VehicleTypes.labelFor(typeKey),
                    new ArrayList<>(crewRefIds),
                    crewViews,
                    Besatzungsstaerke.format(crewPersons),
                    einheitsfuehrerPersonId,
                    maschinistPersonId,
                    involvedInIncident,
                    manuallyInvolvedInIncident));
        }

        KraefteFahrzeugeState.KraefteVehicleView beteiligt = buildVirtualSlotView(
                IncidentCrewSupport.BETEILIGT_VEHICLE_ID,
                IncidentCrewSupport.BETEILIGT_VEHICLE_NAME,
                beteiligtCrewIds,
                personById,
                rowByRefId,
                sortOrderByRefId,
                sourceByRefId,
                paByRefId,
                unitId);
        KraefteFahrzeugeState.KraefteVehicleView einsatzstelle = buildVirtualSlotView(
                IncidentCrewSupport.EINSATZSTELLE_VEHICLE_ID,
                IncidentCrewSupport.EINSATZSTELLE_VEHICLE_NAME,
                einsatzstelleCrewIds,
                personById,
                rowByRefId,
                sortOrderByRefId,
                sourceByRefId,
                paByRefId,
                unitId);
        KraefteFahrzeugeState.KraefteVehicleView wache = buildVirtualSlotView(
                IncidentCrewSupport.WACHE_VEHICLE_ID,
                IncidentCrewSupport.WACHE_VEHICLE_NAME,
                wacheCrewIds,
                personById,
                rowByRefId,
                sortOrderByRefId,
                sourceByRefId,
                paByRefId,
                unitId);

        return new KraefteFahrzeugeState(
                manualPersons, diveraPersons, foreignPersons, beteiligt, einsatzstelle, wache, vehicles);
    }

    private void applyPresetCrewAssignments(
            List<CrewAssignment> assignments,
            Map<Long, Person> personById,
            Map<Long, List<Long>> crewByVehicleId,
            Map<Long, Map<Long, IncidentVehicleCrewRole>> roleByVehicleAndPerson,
            List<Long> beteiligtCrewIds,
            List<Long> einsatzstelleCrewIds,
            List<Long> wacheCrewIds,
            Map<Long, Boolean> involvedByVehicleId,
            Set<Long> onVehicleRefIds,
            Map<Long, Boolean> paByRefId) {
        LinkedHashSet<Long> allPersonIds = new LinkedHashSet<>();
        for (CrewAssignment assignment : assignments) {
            if (assignment.personIds() != null) {
                assignment.personIds().stream().filter(Objects::nonNull).forEach(allPersonIds::add);
            }
            if (assignment.einheitsfuehrerPersonId() != null) {
                allPersonIds.add(assignment.einheitsfuehrerPersonId());
            }
            if (assignment.maschinistPersonId() != null) {
                allPersonIds.add(assignment.maschinistPersonId());
            }
            if (assignment.paPersonIds() != null) {
                assignment.paPersonIds().stream().filter(Objects::nonNull).forEach(allPersonIds::add);
            }
        }
        if (!allPersonIds.isEmpty()) {
            personRepository
                    .findActiveByIdIn(allPersonIds, includeTestReports())
                    .forEach(person -> personById.putIfAbsent(person.getId(), person));
        }
        for (CrewAssignment assignment : assignments) {
            long vehicleId = assignment.vehicleId();
            List<Long> personIds =
                    assignment.personIds() != null ? assignment.personIds() : List.of();
            if (vehicleId == IncidentCrewSupport.BETEILIGT_VEHICLE_ID) {
                for (Long personId : personIds) {
                    if (personId != null && !onVehicleRefIds.contains(personId)) {
                        beteiligtCrewIds.add(personId);
                        onVehicleRefIds.add(personId);
                    }
                }
            } else if (vehicleId == IncidentCrewSupport.EINSATZSTELLE_VEHICLE_ID) {
                for (Long personId : personIds) {
                    if (personId != null && !onVehicleRefIds.contains(personId)) {
                        einsatzstelleCrewIds.add(personId);
                        onVehicleRefIds.add(personId);
                    }
                }
            } else if (vehicleId == IncidentCrewSupport.WACHE_VEHICLE_ID) {
                for (Long personId : personIds) {
                    if (personId != null && !onVehicleRefIds.contains(personId)) {
                        wacheCrewIds.add(personId);
                        onVehicleRefIds.add(personId);
                    }
                }
            } else if (vehicleId > 0) {
                List<Long> crewList = crewByVehicleId.computeIfAbsent(vehicleId, k -> new ArrayList<>());
                Map<Long, IncidentVehicleCrewRole> roles =
                        roleByVehicleAndPerson.computeIfAbsent(vehicleId, k -> new LinkedHashMap<>());
                for (Long personId : personIds) {
                    if (personId == null || onVehicleRefIds.contains(personId)) {
                        continue;
                    }
                    crewList.add(personId);
                    onVehicleRefIds.add(personId);
                }
                if (assignment.einheitsfuehrerPersonId() != null) {
                    Long efId = assignment.einheitsfuehrerPersonId();
                    roles.put(efId, IncidentVehicleCrewRole.EINHEITSFUEHRER);
                    if (!onVehicleRefIds.contains(efId)) {
                        crewList.add(efId);
                        onVehicleRefIds.add(efId);
                    }
                }
                if (assignment.maschinistPersonId() != null) {
                    Long maId = assignment.maschinistPersonId();
                    roles.put(maId, IncidentVehicleCrewRole.MASCHINIST);
                    if (!onVehicleRefIds.contains(maId)) {
                        crewList.add(maId);
                        onVehicleRefIds.add(maId);
                    }
                }
                if (assignment.involvedInIncident() != null) {
                    involvedByVehicleId.put(vehicleId, assignment.isInvolvedInIncident());
                } else if (!personIds.isEmpty()) {
                    involvedByVehicleId.put(vehicleId, true);
                }
                if (Boolean.TRUE.equals(assignment.manuallyInvolvedInIncident()) && personIds.isEmpty()) {
                    involvedByVehicleId.put(vehicleId, true);
                }
            }
            if (assignment.paPersonIds() != null) {
                for (Long paId : assignment.paPersonIds()) {
                    if (paId != null) {
                        paByRefId.put(paId, true);
                    }
                }
            }
        }
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
                        paPersonIds,
                        payload.involvedInIncident(),
                        payload.manuallyInvolvedInIncident()));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    public IncidentReport requireReport(long unitId, long reportId) {
        return incidentReportRepository
                .findByIdAndUnitId(reportId, unitId, includeTestReports())
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
        form.setPersonDamageDetailsJson(PersonDamageDetailsSupport.emptyJson());
        form.setDamagePerpetratorJson(DamagePerpetratorSupport.emptyJson());
        return form;
    }

    public String suggestIncidentNumber(long unitId, LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return IncidentNumberSupport.suggestForDate(
                date,
                incidentReportRepository.findIncidentNumbersForYear(
                        unitId, yearPrefix(date.getYear()), includeTestReports()));
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
        report.setIncidentNumber(resolveIncidentNumberForCreate(unitId, form.incidentDate(), form.incidentNumber()));
        applyCreator(report, actor);
        IncidentReport saved = incidentReportRepository.save(report);
        saveCrewAssignments(saved, form, unitId);
        saveDeployedEquipment(saved, form, unitId);
        syncPaAtemschutzRecords(saved, form, actor);
        return saved;
    }

    @Transactional
    public void delete(long unitId, long reportId, AppUserDetails actor, boolean canApprove) {
        IncidentReport report = requireReport(unitId, reportId);
        ensureWritableReportInTestMode(report);
        if (!EinsatzberichtAccess.canDelete(report, actor, canApprove)) {
            throw new IllegalArgumentException("Dieser Einsatzbericht kann nicht gelöscht werden.");
        }
        long id = report.getId();
        atemschutzService.deleteIncidentPaFitnessRecords(id);
        incidentReportPersonnelRepository.deleteByIncidentReportId(id);
        incidentReportVehicleRepository.deleteByIncidentReportId(id);
        incidentReportEquipmentRepository.deleteByIncidentReportId(id);
        einsatzberichtAttachmentService.deleteAllForReport(id);
        incidentReportRepository.delete(report);
    }

    @Transactional
    public void refreshDiveraFromLatestAlarmData(long unitId, long reportId) {
        IncidentReport report = requireReport(unitId, reportId);
        ensureWritableReportInTestMode(report);
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
                .findByUnitIdAndDiveraAlarmId(unitId, details.alarmId(), includeTestReports())
                .ifPresent(report -> {
                    if (testModeService.isEnabled() && !report.isTestData()) {
                        return;
                    }
                    importDiveraPersonnel(report, details, unitId);
                });
    }

    @Transactional
    public boolean createDraftFromDiveraIfMissing(long unitId, DiveraAlarmDetails details) {
        if (details == null || details.alarmId() <= 0) {
            return false;
        }
        if (incidentReportRepository
                .findByUnitIdAndDiveraAlarmId(unitId, details.alarmId(), includeTestReports())
                .isPresent()) {
            return false;
        }
        IncidentReport report = newDraft(unitId);
        applyDiveraDetails(report, details, unitId);
        report.setStatus(IncidentReportStatus.ENTWURF);
        report.setDiveraAlarmId(details.alarmId());
        report.setDiveraForeignId(details.externalId());
        report.setIncidentNumber(suggestIncidentNumber(unitId, report.getIncidentDate()));
        report.setCreatedByName("DIVERA");
        IncidentReport saved = incidentReportRepository.save(report);
        importDiveraPersonnel(saved, details, unitId);
        return true;
    }

    @Transactional
    public IncidentReport update(
            long unitId,
            long reportId,
            EinsatzberichtFormData form,
            String changeComment,
            AppUserDetails actor,
            boolean canApprove) {
        validateRequired(form);
        IncidentReport report = requireReport(unitId, reportId);
        ensureWritableReportInTestMode(report);
        if (!EinsatzberichtAccess.canEdit(report, actor, canApprove)) {
            throw new IllegalArgumentException("Dieser Einsatzbericht kann nicht bearbeitet werden.");
        }
        if (report.getStatus() != IncidentReportStatus.ENTWURF
                && (actor == null || !actor.getRole().isAdminLevel())) {
            throw new IllegalArgumentException("Freigegebene oder archivierte Berichte können nur von Administratoren geändert werden.");
        }
        boolean trackChanges = report.getStatus() != IncidentReportStatus.ENTWURF;
        Map<String, String> before = trackChanges ? IncidentReportSnapshot.fromReport(report) : Map.of();
        applyForm(report, form, unitId);
        List<IncidentReportSnapshot.FieldChange> fieldChanges = List.of();
        if (trackChanges) {
            Map<String, String> after = IncidentReportSnapshot.fromReport(report);
            fieldChanges = IncidentReportSnapshot.diff(before, after);
        }
        IncidentReport saved = incidentReportRepository.save(report);
        saveCrewAssignments(saved, form, unitId);
        saveDeployedEquipment(saved, form, unitId);
        syncPaAtemschutzRecords(saved, form, actor);
        if (trackChanges) {
            recordChange(saved, actor, changeComment, fieldChanges);
        }
        return saved;
    }

    private void recordChange(
            IncidentReport report,
            AppUserDetails actor,
            String changeComment,
            List<IncidentReportSnapshot.FieldChange> fieldChanges) {
        if (report.getStatus() == IncidentReportStatus.ENTWURF) {
            return;
        }
        String comment = changeComment != null ? changeComment.trim() : "";
        if (fieldChanges.isEmpty() && comment.isBlank()) {
            return;
        }
        IncidentReportChange change = new IncidentReportChange();
        change.setIncidentReport(report);
        if (actor != null) {
            userRepository.findById(actor.getUserId()).ifPresent(change::setChangedByUser);
            change.setChangedByName(actor.getDisplayName());
        }
        change.setCommentText(comment.isBlank() ? null : comment);
        for (IncidentReportSnapshot.FieldChange fieldChange : fieldChanges) {
            IncidentReportChangeField field = new IncidentReportChangeField();
            field.setChange(change);
            field.setFieldKey(fieldChange.key());
            field.setFieldLabel(fieldChange.label());
            field.setOldValue(fieldChange.oldValue());
            field.setNewValue(fieldChange.newValue());
            change.getFields().add(field);
        }
        incidentReportChangeRepository.save(change);
    }

    private EinsatzberichtListItemView toListItem(IncidentReport report) {
        User creator = report.getCreatedByUser();
        return new EinsatzberichtListItemView(
                report.getId(),
                report.getIncidentNumber(),
                report.getIncidentDate(),
                displayStichwort(report),
                report.getLocation(),
                report.getStatus().cssModifier(),
                report.getStatus().label(),
                report.getDiveraAlarmId() != null,
                creator != null ? creator.getId() : null);
    }

    private static String displayStichwort(IncidentReport report) {
        if (report.getStichwort() != null && !report.getStichwort().isBlank()) {
            return report.getStichwort().trim();
        }
        return report.getIncidentTypeLabel() != null ? report.getIncidentTypeLabel().trim() : "";
    }

    @Transactional(readOnly = true)
    public List<VehicleEquipmentView> listVehicleEquipment(long unitId, List<Long> vehicleIds) {
        if (vehicleIds == null || vehicleIds.isEmpty()) {
            return List.of();
        }
        List<VehicleEquipmentView> result = new ArrayList<>();
        for (Long vehicleId : vehicleIds) {
            if (vehicleId == null || vehicleId <= 0) {
                continue;
            }
            Vehicle vehicle =
                    vehicleRepository.findByIdAndUnitId(vehicleId, unitId).orElse(null);
            if (vehicle == null) {
                continue;
            }
            List<VehicleEquipmentView.EquipmentItemView> items = vehicleEquipmentRepository
                    .findByVehicleIdWithCategoryOrderBySortOrderAscNameAsc(vehicleId)
                    .stream()
                    .map(eq -> new VehicleEquipmentView.EquipmentItemView(
                            eq.getId(),
                            eq.getName(),
                            eq.getCategory() != null ? eq.getCategory().getId() : null,
                            eq.getCategory() != null ? eq.getCategory().getName() : null))
                    .toList();
            result.add(new VehicleEquipmentView(vehicle.getId(), vehicle.getName(), items));
        }
        return result;
    }

    public List<DeployedEquipmentAssignment> parseDeployedEquipment(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<DeployedEquipmentPayload> payloads = objectMapper.readValue(json, DEPLOYED_EQUIPMENT_LIST);
            List<DeployedEquipmentAssignment> result = new ArrayList<>();
            for (DeployedEquipmentPayload payload : payloads) {
                if (payload == null || payload.vehicleId() == null) {
                    continue;
                }
                List<Long> equipmentIds = payload.equipmentIds() != null
                        ? payload.equipmentIds().stream().filter(Objects::nonNull).toList()
                        : List.of();
                result.add(new DeployedEquipmentAssignment(payload.vehicleId(), equipmentIds));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    public String buildDeployedEquipmentJson(long reportId) {
        Map<Long, List<Long>> equipmentByVehicleId = new LinkedHashMap<>();
        for (IncidentReportEquipment row : incidentReportEquipmentRepository.findByIncidentReportId(reportId)) {
            if (row.getVehicleEquipment() == null) {
                continue;
            }
            equipmentByVehicleId
                    .computeIfAbsent(row.getVehicle().getId(), ignored -> new ArrayList<>())
                    .add(row.getVehicleEquipment().getId());
        }
        List<DeployedEquipmentAssignment> assignments = equipmentByVehicleId.entrySet().stream()
                .map(entry -> new DeployedEquipmentAssignment(entry.getKey(), entry.getValue()))
                .toList();
        try {
            return objectMapper.writeValueAsString(assignments);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void applyForm(IncidentReport report, EinsatzberichtFormData form, long unitId) {
        String stichwort = form.stichwort() != null ? form.stichwort().trim() : "";
        if (form.incidentNumber() != null && !form.incidentNumber().isBlank()) {
            report.setIncidentNumber(form.incidentNumber().trim());
        }
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
        report.setChargeable(form.chargeable());
        report.setFireWatch(form.fireWatch());
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
        boolean personDamagesActive = form.personDamagesEnabled()
                || form.personsRescued() > 0
                || form.personsInjured() > 0
                || form.personsRecovered() > 0
                || form.personsDead() > 0;
        report.setPersonDamagesEnabled(personDamagesActive);
        report.setAnimalDamagesEnabled(form.animalDamagesEnabled());
        if (personDamagesActive) {
            int rescued = Math.max(0, form.personsRescued());
            int injured = Math.max(0, form.personsInjured());
            int recovered = Math.max(0, form.personsRecovered());
            int dead = Math.max(0, form.personsDead());
            report.setPersonsRescued(rescued);
            report.setPersonsInjured(injured);
            report.setPersonsRecovered(recovered);
            report.setPersonsDead(dead);
            PersonDamageDetails details = PersonDamageDetailsSupport.parse(form.personDamageDetailsJson())
                    .normalized(rescued, injured, recovered, dead);
            report.setPersonDamageDetailsJson(PersonDamageDetailsSupport.serialize(details));
        } else {
            report.setPersonsRescued(0);
            report.setPersonsInjured(0);
            report.setPersonsRecovered(0);
            report.setPersonsDead(0);
            report.setPersonDamageDetailsJson(PersonDamageDetailsSupport.emptyJson());
        }
        report.setDamagePerpetratorJson(DamagePerpetratorSupport.serialize(
                DamagePerpetratorSupport.parse(form.damagePerpetratorJson()).normalized()));
        report.setPersonsEvacuated(0);
        report.setPersonsInjuredOwn(0);
        report.setPersonsDeadOwn(0);
        if (form.animalDamagesEnabled()) {
            report.setAnimalsRescued(Math.max(0, form.animalsRescued()));
            report.setAnimalsInjured(Math.max(0, form.animalsInjured()));
            report.setAnimalsRecovered(Math.max(0, form.animalsRecovered()));
            report.setAnimalsDead(Math.max(0, form.animalsDead()));
        } else {
            report.setAnimalsRescued(0);
            report.setAnimalsInjured(0);
            report.setAnimalsRecovered(0);
            report.setAnimalsDead(0);
        }
        report.setVehicleDamage(null);
        report.setEquipmentDamage(null);
        MaterialDamageEntries materialDamages =
                MaterialDamageEntriesSupport.parse(form.materialDamageEntriesJson()).normalized();
        report.setMaterialDamageEntriesJson(MaterialDamageEntriesSupport.serialize(materialDamages));
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

    private List<CrewAssignment> withCommanderInBeteiligt(List<CrewAssignment> assignments, IncidentReport report) {
        Person commander = report.getCommanderPerson();
        if (commander == null) {
            return assignments;
        }
        long commanderId = commander.getId();
        boolean alreadyAssigned = assignments.stream()
                .anyMatch(assignment -> assignment.personIds() != null && assignment.personIds().contains(commanderId));
        if (alreadyAssigned) {
            return assignments;
        }
        long beteiligtId = IncidentCrewSupport.BETEILIGT_VEHICLE_ID;
        List<CrewAssignment> result = new ArrayList<>(assignments);
        Optional<CrewAssignment> beteiligtAssignment = result.stream()
                .filter(assignment -> assignment.vehicleId() == beteiligtId)
                .findFirst();
        if (beteiligtAssignment.isPresent()) {
            CrewAssignment existing = beteiligtAssignment.get();
            List<Long> personIds = new ArrayList<>(existing.personIds() != null ? existing.personIds() : List.of());
            personIds.add(commanderId);
            int index = result.indexOf(existing);
            result.set(
                    index,
                    new CrewAssignment(
                            beteiligtId,
                            personIds,
                            existing.einheitsfuehrerPersonId(),
                            existing.maschinistPersonId(),
                            existing.paPersonIds(),
                            existing.involvedInIncident(),
                            existing.manuallyInvolvedInIncident()));
        } else {
            result.add(new CrewAssignment(beteiligtId, List.of(commanderId)));
        }
        return result;
    }

    private void saveCrewAssignments(IncidentReport report, EinsatzberichtFormData form, long unitId) {
        long reportId = report.getId();
        Map<Long, IncidentPersonnelSource> existingSources = loadExistingSources(reportId);
        Map<Long, String> existingUcrNames = loadExistingUcrNames(reportId);
        List<IncidentReportPersonnel> previousReserveRows =
                incidentReportPersonnelRepository.findByIncidentReportId(reportId).stream()
                        .filter(row -> row.getIncidentReportVehicle() == null)
                        .map(this::copyReserveRow)
                        .toList();
        incidentReportPersonnelRepository.deleteByIncidentReportId(reportId);
        incidentReportVehicleRepository.deleteByIncidentReportId(reportId);

        List<CrewAssignment> assignments = withCommanderInBeteiligt(
                form.crewAssignments() != null ? form.crewAssignments() : List.of(), report);
        Map<Long, CrewAssignment> assignmentByVehicleId = new HashMap<>();
        for (CrewAssignment assignment : assignments) {
            if (assignment.vehicleId() > 0) {
                assignmentByVehicleId.put(assignment.vehicleId(), assignment);
            }
        }

        Map<Long, IncidentReportVehicle> reportVehicleByUnitVehicleId = new HashMap<>();
        for (Vehicle vehicle : listVehiclesForForm(unitId)) {
            IncidentReportVehicle row = new IncidentReportVehicle();
            row.setIncidentReport(report);
            row.setVehicle(vehicle);
            row.setVehicleName(vehicle.getName());
            CrewAssignment vehicleAssignment = assignmentByVehicleId.get(vehicle.getId());
            boolean hasAssignedCrew = vehicleAssignment != null
                    && vehicleAssignment.personIds() != null
                    && !vehicleAssignment.personIds().isEmpty();
            row.setInvolved(vehicleAssignment != null
                    && (vehicleAssignment.isInvolvedInIncident() || hasAssignedCrew));
            IncidentReportVehicle saved = incidentReportVehicleRepository.save(row);
            reportVehicleByUnitVehicleId.put(vehicle.getId(), saved);
        }

        Set<Long> assignedPersons = new HashSet<>();

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
            for (Long personRefId : assignment.personIds()) {
                if (personRefId == null || !assignedPersons.add(personRefId)) {
                    continue;
                }
                IncidentReportPersonnel row = new IncidentReportPersonnel();
                row.setIncidentReport(report);
                row.setIncidentReportVehicle(reportVehicle);
                if (IncidentPersonnelRefs.isUcrRef(personRefId)) {
                    long ucrId = IncidentPersonnelRefs.ucrFromRef(personRefId);
                    row.setPerson(null);
                    row.setDiveraUcrId(String.valueOf(ucrId));
                    row.setDisplayName(existingUcrNames.getOrDefault(
                            ucrId, IncidentPersonnelRefs.displayNameForUcr(ucrId)));
                    row.setSource(IncidentPersonnelSource.DIVERA);
                } else {
                    Person person = resolvePersonForReport(personRefId, unitId);
                    row.setPerson(person);
                    row.setDisplayName(person.anwesenheitDisplayName());
                    IncidentPersonnelSource source =
                            existingSources.getOrDefault(personRefId, IncidentPersonnelSource.MANUAL);
                    row.setSource(source);
                    if (person.getUnit() != null && person.getUnit().getId() != unitId) {
                        row.setSource(IncidentPersonnelSource.FOREIGN);
                        row.setForeignUnit(person.getUnit());
                    }
                }
                if (personRefId.equals(einheitsfuehrerPersonId)) {
                    row.setVehicleRole(IncidentVehicleCrewRole.EINHEITSFUEHRER);
                } else if (personRefId.equals(maschinistPersonId)) {
                    row.setVehicleRole(IncidentVehicleCrewRole.MASCHINIST);
                }
                row.setUsesPa(paPersonIds.contains(personRefId));
                incidentReportPersonnelRepository.save(row);
            }
        }
        for (IncidentReportPersonnel reserve : previousReserveRows) {
            long refId = personnelRefId(reserve);
            if (assignedPersons.contains(refId)) {
                continue;
            }
            IncidentReportPersonnel row = copyReserveRow(reserve);
            row.setIncidentReport(report);
            row.setIncidentReportVehicle(null);
            incidentReportPersonnelRepository.save(row);
        }
    }

    private IncidentReportPersonnel copyReserveRow(IncidentReportPersonnel source) {
        IncidentReportPersonnel copy = new IncidentReportPersonnel();
        copy.setPerson(source.getPerson());
        copy.setDisplayName(source.getDisplayName());
        copy.setSource(source.getSource());
        copy.setDiveraUcrId(source.getDiveraUcrId());
        copy.setForeignUnit(source.getForeignUnit());
        copy.setUsesPa(source.isUsesPa());
        return copy;
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
            assignment.paPersonIds().stream()
                    .filter(Objects::nonNull)
                    .filter(id -> !IncidentPersonnelRefs.isUcrRef(id))
                    .forEach(result::add);
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
            try {
                sources.put(personnelRefId(row), row.getSource());
            } catch (IllegalStateException ex) {
                log.warn(
                        "Personaleintrag {} in Bericht {} beim Quellen-Laden übersprungen: {}",
                        row.getId(),
                        reportId,
                        ex.getMessage());
            }
        }
        return sources;
    }

    private Map<Long, String> loadExistingUcrNames(long reportId) {
        Map<Long, String> names = new HashMap<>();
        for (IncidentReportPersonnel row : incidentReportPersonnelRepository.findByIncidentReportId(reportId)) {
            if (row.getDiveraUcrId() == null || row.getDiveraUcrId().isBlank()) {
                continue;
            }
            try {
                names.put(Long.parseLong(row.getDiveraUcrId().trim()), row.getDisplayName());
            } catch (NumberFormatException ignored) {
                // ignore malformed UCR
            }
        }
        return names;
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
            List<Long> crewRefIds,
            Map<Long, Person> personById,
            Map<Long, IncidentReportPersonnel> rowByRefId,
            Map<Long, Integer> sortOrderByRefId,
            Map<Long, IncidentPersonnelSource> sourceByRefId,
            Map<Long, Boolean> paByRefId,
            long reportUnitId) {
        List<Person> crewPersons = crewRefIds.stream()
                .map(personById::get)
                .filter(Objects::nonNull)
                .toList();
        return new KraefteFahrzeugeState.KraefteVehicleView(
                slotId,
                slotName,
                null,
                null,
                new ArrayList<>(crewRefIds),
                crewRefIds.stream()
                        .map(refId -> toPersonViewFromRef(
                                refId,
                                personById,
                                rowByRefId,
                                sortOrderByRefId,
                                null,
                                paByRefId.getOrDefault(refId, false),
                                sourceByRefId.get(refId),
                                reportUnitId))
                        .toList(),
                Besatzungsstaerke.format(crewPersons),
                null,
                null,
                false,
                false);
    }

    private static String poolSourceFor(IncidentPersonnelSource source) {
        if (source == IncidentPersonnelSource.DIVERA) {
            return "divera";
        }
        if (source == IncidentPersonnelSource.FOREIGN) {
            return "foreign";
        }
        return "manual";
    }

    private KraefteFahrzeugeState.KraeftePersonView toPersonView(
            Person person,
            Map<Long, Integer> sortOrderByRefId,
            IncidentVehicleCrewRole vehicleRole,
            boolean usesPa,
            String poolSource,
            String unitLabel) {
        return new KraefteFahrzeugeState.KraeftePersonView(
                person.getId(),
                person.anwesenheitDisplayName(),
                Besatzungsstaerke.qualTier(person).name(),
                sortOrderByRefId.getOrDefault(person.getId(), 0),
                vehicleRole != null ? vehicleRole.name() : null,
                usesPa,
                poolSource,
                unitLabel,
                person.getDiveraUcrId(),
                false);
    }

    private KraefteFahrzeugeState.KraeftePersonView toUcrPersonView(
            long refId, long ucrId, String displayName, int sortOrder) {
        String label = displayName != null && !displayName.isBlank()
                ? displayName
                : IncidentPersonnelRefs.displayNameForUcr(ucrId);
        return new KraefteFahrzeugeState.KraeftePersonView(
                refId,
                label,
                Besatzungsstaerke.QualTier.MANNSCHAFT.name(),
                sortOrder,
                null,
                false,
                "divera",
                null,
                String.valueOf(ucrId),
                true);
    }

    private KraefteFahrzeugeState.KraeftePersonView toPersonViewFromRef(
            long refId,
            Map<Long, Person> personById,
            Map<Long, IncidentReportPersonnel> rowByRefId,
            Map<Long, Integer> sortOrderByRefId,
            IncidentVehicleCrewRole vehicleRole,
            boolean usesPa,
            IncidentPersonnelSource source,
            long reportUnitId) {
        if (IncidentPersonnelRefs.isUcrRef(refId)) {
            long ucrId = IncidentPersonnelRefs.ucrFromRef(refId);
            IncidentReportPersonnel row = rowByRefId.get(refId);
            String displayName = row != null ? row.getDisplayName() : IncidentPersonnelRefs.displayNameForUcr(ucrId);
            return toUcrPersonView(refId, ucrId, displayName, sortOrderByRefId.getOrDefault(refId, 0));
        }
        Person person = personById.get(refId);
        if (person == null) {
            IncidentReportPersonnel row = rowByRefId.get(refId);
            String displayName = row != null ? row.getDisplayName() : "Unbekannt";
            return new KraefteFahrzeugeState.KraeftePersonView(
                    refId, displayName, Besatzungsstaerke.QualTier.MANNSCHAFT.name(), 0, null, usesPa, "manual", null, null, false);
        }
        return toPersonView(
                person,
                sortOrderByRefId,
                vehicleRole,
                usesPa,
                poolSourceFor(source),
                unitLabelForPerson(person, reportUnitId));
    }

    private static String unitLabelForPerson(Person person, long reportUnitId) {
        if (person == null || person.getUnit() == null) {
            return null;
        }
        return person.getUnit().getId() != reportUnitId ? person.getUnit().getName() : null;
    }

    private long personnelRefId(IncidentReportPersonnel row) {
        if (row.getPerson() != null) {
            return row.getPerson().getId();
        }
        if (row.getDiveraUcrId() != null && !row.getDiveraUcrId().isBlank()) {
            try {
                return IncidentPersonnelRefs.refFromUcr(Long.parseLong(row.getDiveraUcrId().trim()));
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Ungültige DIVERA-UCR-ID: " + row.getDiveraUcrId());
            }
        }
        throw new IllegalStateException("Personaleintrag ohne Person und ohne DIVERA-UCR");
    }

    private Person resolvePersonForReport(long personId, long reportUnitId) {
        Person person = personalService.requirePerson(personId);
        if (person.getUnit().getId() == reportUnitId) {
            return person;
        }
        return person;
    }

    @Transactional
    public void transitionStatus(
            long unitId, long reportId, IncidentReportStatus newStatus, AppUserDetails actor, boolean canApprove) {
        if (actor == null) {
            throw new IllegalArgumentException("Keine Berechtigung für diese Aktion.");
        }
        if (!canApprove && !actor.getRole().isAdminLevel()) {
            throw new IllegalArgumentException("Keine Berechtigung zum Ändern des Status.");
        }
        IncidentReport report = requireReport(unitId, reportId);
        ensureWritableReportInTestMode(report);
        validateStatusTransition(report.getStatus(), newStatus);
        report.setStatus(newStatus);
        if (newStatus == IncidentReportStatus.FREIGEGEBEN) {
            userRepository.findById(actor.getUserId()).ifPresent(report::setReleasedByUser);
            report.setReleasedAt(Instant.now());
        }
        incidentReportRepository.save(report);
    }

    private static void validateStatusTransition(IncidentReportStatus from, IncidentReportStatus to) {
        boolean valid = (from == IncidentReportStatus.ENTWURF && to == IncidentReportStatus.FREIGEGEBEN)
                || (from == IncidentReportStatus.FREIGEGEBEN && to == IncidentReportStatus.ARCHIVIERT);
        if (!valid) {
            throw new IllegalArgumentException(
                    "Ungültiger Status-Übergang: " + from.label() + " → " + to.label());
        }
    }

    private void applyCreator(IncidentReport report, AppUserDetails actor) {
        if (actor == null) {
            return;
        }
        User user = userRepository.findById(actor.getUserId()).orElse(null);
        report.setCreatedByUser(user);
        report.setCreatedByName(actor.getDisplayName());
    }

    private String resolveIncidentNumberForCreate(long unitId, LocalDate date, String requestedNumber) {
        if (requestedNumber != null && !requestedNumber.isBlank()) {
            return requestedNumber.trim();
        }
        return suggestIncidentNumber(unitId, date);
    }

    private static String yearPrefix(int year) {
        return year + "-";
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
        try {
            importDiveraPersonnelInternal(report, details, unitId);
        } catch (Exception e) {
            log.warn(
                    "[Divera→Personal] Import fehlgeschlagen unit={} alarm={} report={}: {}",
                    unitId,
                    details != null ? details.alarmId() : null,
                    report != null ? report.getId() : null,
                    e.getMessage(),
                    e);
        }
    }

    private void importDiveraPersonnelInternal(IncidentReport report, DiveraAlarmDetails details, long unitId) {
        UnitBerichteSettings settings = berichteSettingsService.ensureSettings(unitId);
        if (!settings.isImportPersonnelFromDivera()) {
            return;
        }
        List<String> allowedStatusIds = berichteSettingsService.parsePersonnelStatusIds(settings);
        DiveraUserDirectory diveraUsers = loadDiveraUserDirectory(unitId);
        Map<Long, Integer> statusByUcr = diveraUsers.statusByUcr();
        Set<Long> targetUcrIds = resolveTargetUcrIds(details, allowedStatusIds, statusByUcr);
        if (targetUcrIds.isEmpty()) {
            log.info(
                    "[Divera→Personal] unit={} alarm={} report={} — keine UCR-IDs "
                            + "(ucr_answered leer oder keine passende Status-ID? hits={} ucrIds={} statusFilter={})",
                    unitId,
                    details.alarmId(),
                    report.getId(),
                    details.answeredHits() != null ? details.answeredHits().size() : 0,
                    details.answeredUcrIds() != null ? details.answeredUcrIds().size() : 0,
                    allowedStatusIds);
            return;
        }
        boolean autoAnwesenheit = settings.isAutoAssignDiveraPersonnelToAnwesenheit();
        IncidentReportVehicle anwesenheitVehicle = autoAnwesenheit ? resolveBeteiligtVehicle(report) : null;
        boolean testData = testModeService.isEnabled();
        Map<Long, String> displayNameByUcr = displayNamesByUcr(details, diveraUsers.displayNameByUcr());
        List<IncidentReportPersonnel> existingRows =
                incidentReportPersonnelRepository.findByIncidentReportId(report.getId());
        Set<Long> alreadyPresentPersons = existingRows.stream()
                .map(IncidentReportPersonnel::getPerson)
                .filter(Objects::nonNull)
                .map(Person::getId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> alreadyPresentUcrs = existingRows.stream()
                .map(IncidentReportPersonnel::getDiveraUcrId)
                .filter(ucr -> ucr != null && !ucr.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(HashSet::new));
        if (autoAnwesenheit && anwesenheitVehicle != null) {
            for (IncidentReportPersonnel row : existingRows) {
                if (row.getSource() != IncidentPersonnelSource.DIVERA || row.getIncidentReportVehicle() != null) {
                    continue;
                }
                row.setIncidentReportVehicle(anwesenheitVehicle);
                incidentReportPersonnelRepository.save(row);
            }
        }
        refreshDiveraPersonnelDisplayNames(existingRows, targetUcrIds, displayNameByUcr, unitId, testData);
        Set<Long> assignedPersons = new HashSet<>(alreadyPresentPersons);
        int added = 0;
        int unmatched = 0;
        for (Long ucrId : targetUcrIds) {
            String ucr = String.valueOf(ucrId);
            if (alreadyPresentUcrs.contains(ucr)) {
                continue;
            }
            Optional<Person> personOpt = resolvePersonForDiveraUcr(unitId, ucrId, testData);
            if (personOpt.isPresent()) {
                Person person = personOpt.get();
                if (!assignedPersons.add(person.getId())) {
                    continue;
                }
                IncidentReportPersonnel row = new IncidentReportPersonnel();
                row.setIncidentReport(report);
                row.setPerson(person);
                row.setIncidentReportVehicle(anwesenheitVehicle);
                row.setDisplayName(person.anwesenheitDisplayName());
                row.setDiveraUcrId(ucr);
                row.setSource(IncidentPersonnelSource.DIVERA);
                if (person.getUnit() != null && person.getUnit().getId() != unitId) {
                    row.setSource(IncidentPersonnelSource.FOREIGN);
                    row.setForeignUnit(person.getUnit());
                }
                incidentReportPersonnelRepository.save(row);
                added++;
                continue;
            }
            unmatched++;
            IncidentReportPersonnel row = new IncidentReportPersonnel();
            row.setIncidentReport(report);
            row.setPerson(null);
            row.setIncidentReportVehicle(anwesenheitVehicle);
            row.setDiveraUcrId(ucr);
            row.setDisplayName(resolveDiveraDisplayName(ucrId, displayNameByUcr));
            row.setSource(IncidentPersonnelSource.DIVERA);
            incidentReportPersonnelRepository.save(row);
            alreadyPresentUcrs.add(ucr);
            added++;
        }
        report.setStrengthCrew(assignedPersons.size() + alreadyPresentUcrs.size());
        if (added > 0) {
            incidentReportRepository.save(report);
        }
        if (added > 0 || unmatched > 0) {
            log.info(
                    "[Divera→Personal] unit={} alarm={} report={} ucr={} neu={} ohneZuordnung={} autoAnwesenheit={}",
                    unitId,
                    details.alarmId(),
                    report.getId(),
                    targetUcrIds.size(),
                    added,
                    unmatched,
                    autoAnwesenheit);
        }
    }

    private IncidentReportVehicle resolveBeteiligtVehicle(IncidentReport report) {
        return incidentReportVehicleRepository.findByIncidentReportId(report.getId()).stream()
                .filter(row -> row.getVehicle() == null
                        && IncidentCrewSupport.BETEILIGT_VEHICLE_NAME.equals(row.getVehicleName()))
                .findFirst()
                .orElseGet(() -> {
                    IncidentReportVehicle row = new IncidentReportVehicle();
                    row.setIncidentReport(report);
                    row.setVehicle(null);
                    row.setVehicleName(IncidentCrewSupport.BETEILIGT_VEHICLE_NAME);
                    row.setInvolved(true);
                    return incidentReportVehicleRepository.saveAndFlush(row);
                });
    }

    private Set<Long> resolveTargetUcrIds(
            DiveraAlarmDetails details, List<String> allowedStatusIds, Map<Long, Integer> statusByUcr) {
        Set<Long> fromAnswered = new LinkedHashSet<>();
        if (details.answeredHits() != null) {
            for (var hit : details.answeredHits()) {
                if (hit.ucrId() == null || hit.ucrId().isBlank()) {
                    continue;
                }
                try {
                    long ucrId = Long.parseLong(hit.ucrId().trim());
                    if (ucrId <= 0) {
                        continue;
                    }
                    if (matchesPersonnelStatusFilter(hit.statusId(), ucrId, allowedStatusIds, statusByUcr)) {
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
                if (ucrId != null
                        && ucrId > 0
                        && matchesPersonnelStatusFilter(null, ucrId, allowedStatusIds, statusByUcr)) {
                    fromAnswered.add(ucrId);
                }
            }
        }
        return fromAnswered;
    }

    /**
     * Status-Filter: verschachteltes {@code ucr_answered} liefert Status pro Treffer;
     * flache UCR-Arrays ohne Status werden bei aktivem Filter über {@code /api/users} aufgelöst.
     */
    private static boolean matchesPersonnelStatusFilter(
            String hitStatusId, long ucrId, List<String> allowedStatusIds, Map<Long, Integer> statusByUcr) {
        if (allowedStatusIds == null || allowedStatusIds.isEmpty()) {
            return true;
        }
        String statusId = hitStatusId != null ? hitStatusId.trim() : "";
        if (statusId.isEmpty()) {
            Integer liveStatus = statusByUcr.get(ucrId);
            if (liveStatus != null && liveStatus > 0) {
                statusId = String.valueOf(liveStatus);
            }
        }
        if (statusId.isEmpty()) {
            return true;
        }
        return allowedStatusIds.contains(statusId);
    }

    private Map<Long, Integer> loadDiveraUserStatuses(long unitId) {
        return loadDiveraUserDirectory(unitId).statusByUcr();
    }

    private DiveraUserDirectory loadDiveraUserDirectory(long unitId) {
        return diveraSettingsRepository
                .findByUnitId(unitId)
                .map(cfg -> diveraApiClient.fetchUserDirectory(
                        cfg.getApiBaseUrl() != null ? cfg.getApiBaseUrl() : DiveraIntegrationSupport.DEFAULT_API_BASE,
                        cfg.getAccessKey()))
                .orElse(DiveraUserDirectory.empty());
    }

    private void refreshDiveraPersonnelDisplayNames(
            List<IncidentReportPersonnel> existingRows,
            Set<Long> targetUcrIds,
            Map<Long, String> displayNameByUcr,
            long unitId,
            boolean testData) {
        Set<Long> validUcrs = targetUcrIds != null ? targetUcrIds : Set.of();
        for (IncidentReportPersonnel row : List.copyOf(existingRows)) {
            if (row.getSource() != IncidentPersonnelSource.DIVERA || row.getDiveraUcrId() == null) {
                continue;
            }
            long ucrId;
            try {
                ucrId = Long.parseLong(row.getDiveraUcrId().trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (!validUcrs.contains(ucrId) && row.getPerson() == null && isPlaceholderDiveraDisplayName(row.getDisplayName())) {
                incidentReportPersonnelRepository.delete(row);
                continue;
            }
            if (row.getPerson() == null) {
                resolvePersonForDiveraUcr(unitId, ucrId, testData).ifPresent(person -> {
                    row.setPerson(person);
                    row.setDisplayName(person.anwesenheitDisplayName());
                    if (person.getUnit() != null && person.getUnit().getId() != unitId) {
                        row.setSource(IncidentPersonnelSource.FOREIGN);
                        row.setForeignUnit(person.getUnit());
                    }
                    incidentReportPersonnelRepository.save(row);
                });
            }
            if (row.getPerson() != null) {
                continue;
            }
            String resolved = resolveDiveraDisplayName(ucrId, displayNameByUcr);
            if (!resolved.equals(row.getDisplayName())) {
                row.setDisplayName(resolved);
                incidentReportPersonnelRepository.save(row);
            }
        }
    }

    private static boolean isPlaceholderDiveraDisplayName(String displayName) {
        return displayName != null && displayName.startsWith("DIVERA UCR ");
    }

    private static String resolveDiveraDisplayName(long ucrId, Map<Long, String> displayNameByUcr) {
        if (displayNameByUcr != null) {
            String name = displayNameByUcr.get(ucrId);
            if (name != null && !name.isBlank()) {
                return name.trim();
            }
        }
        return IncidentPersonnelRefs.displayNameForUcr(ucrId);
    }

    private Optional<Person> resolvePersonForDiveraUcr(long unitId, long ucrId, boolean testData) {
        String ucr = String.valueOf(ucrId);
        Optional<Person> ownUnit = personRepository.findByUnitIdAndDiveraUcrId(unitId, ucr, testData);
        if (ownUnit.isPresent()) {
            return ownUnit;
        }
        if (!berichteSettingsService.isForeignUnitPersonnelAllowed(unitId)) {
            return Optional.empty();
        }
        return personRepository.findAllByDiveraUcrId(ucr, testData).stream().findFirst();
    }

    private static Map<Long, String> displayNamesByUcr(DiveraAlarmDetails details, Map<Long, String> diveraUserNames) {
        Map<Long, String> names = new LinkedHashMap<>();
        if (diveraUserNames != null) {
            names.putAll(diveraUserNames);
        }
        collectDisplayNames(details.answeredHits(), names);
        if (details.personnelHits() != null) {
            collectDisplayNames(details.personnelHits(), names);
        }
        return names;
    }

    private static void collectDisplayNames(
            List<DiveraAlarmDetailsMapper.DiveraPersonnelHit> hits, Map<Long, String> names) {
        if (hits == null) {
            return;
        }
        for (var hit : hits) {
            if (hit.ucrId() == null || hit.ucrId().isBlank()) {
                continue;
            }
            try {
                long ucrId = Long.parseLong(hit.ucrId().trim());
                if (hit.displayName() != null && !hit.displayName().isBlank()) {
                    names.putIfAbsent(ucrId, hit.displayName().trim());
                }
            } catch (NumberFormatException ignored) {
                // ignore malformed UCR
            }
        }
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

    private boolean includeTestReports() {
        return testModeService.isEnabled();
    }

    private void ensureWritableReportInTestMode(IncidentReport report) {
        if (testModeService.isEnabled() && !report.isTestData()) {
            throw new IllegalArgumentException(
                    "Produktiv-Einsatzberichte können im Testmodus nur angesehen werden.");
        }
    }

    private Unit requireUnit(long unitId) {
        return unitRepository
                .findVisibleById(unitId, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
    }

    private void saveDeployedEquipment(IncidentReport report, EinsatzberichtFormData form, long unitId) {
        long reportId = report.getId();
        incidentReportEquipmentRepository.deleteByIncidentReportId(reportId);
        List<DeployedEquipmentAssignment> assignments =
                form.deployedEquipment() != null ? form.deployedEquipment() : List.of();
        for (DeployedEquipmentAssignment assignment : assignments) {
            if (assignment.vehicleId() <= 0) {
                continue;
            }
            Vehicle vehicle = vehicleRepository
                    .findByIdAndUnitId(assignment.vehicleId(), unitId)
                    .orElse(null);
            if (vehicle == null) {
                continue;
            }
            List<Long> equipmentIds =
                    assignment.equipmentIds() != null ? assignment.equipmentIds() : List.of();
            for (Long equipmentId : equipmentIds) {
                if (equipmentId == null) {
                    continue;
                }
                VehicleEquipment equipment = vehicleEquipmentRepository
                        .findById(equipmentId)
                        .filter(eq -> eq.getVehicle().getId().equals(vehicle.getId()))
                        .orElse(null);
                if (equipment == null) {
                    continue;
                }
                IncidentReportEquipment row = new IncidentReportEquipment();
                row.setIncidentReport(report);
                row.setVehicle(vehicle);
                row.setVehicleEquipment(equipment);
                row.setEquipmentName(equipment.getName());
                row.setCategoryName(
                        equipment.getCategory() != null ? equipment.getCategory().getName() : null);
                incidentReportEquipmentRepository.save(row);
            }
        }
    }

    private record CrewAssignmentPayload(
            Long vehicleId,
            List<Long> personIds,
            Long einheitsfuehrerPersonId,
            Long maschinistPersonId,
            List<Long> paPersonIds,
            Boolean involvedInIncident,
            Boolean manuallyInvolvedInIncident) {}

    private record DeployedEquipmentPayload(Long vehicleId, List<Long> equipmentIds) {}
}
