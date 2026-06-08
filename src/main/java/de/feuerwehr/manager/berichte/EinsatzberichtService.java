package de.feuerwehr.manager.berichte;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.technik.Vehicle;
import de.feuerwehr.manager.technik.VehicleRepository;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.LocalDate;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EinsatzberichtService {

    private static final TypeReference<List<CrewAssignmentPayload>> CREW_ASSIGNMENT_LIST =
            new TypeReference<>() {};

    private final IncidentReportRepository incidentReportRepository;
    private final IncidentReportPersonnelRepository incidentReportPersonnelRepository;
    private final IncidentReportVehicleRepository incidentReportVehicleRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final PersonalService personalService;
    private final VehicleRepository vehicleRepository;
    private final TestModeService testModeService;
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
        for (Vehicle vehicle : unitVehicles) {
            crewByVehicleId.put(vehicle.getId(), new ArrayList<>());
        }

        Set<Long> diveraPersonIds = new HashSet<>();
        Set<Long> onVehiclePersonIds = new HashSet<>();

        if (reportId != null) {
            for (IncidentReportPersonnel row : incidentReportPersonnelRepository.findByIncidentReportId(reportId)) {
                Person person = row.getPerson();
                if (person == null) {
                    continue;
                }
                if (row.getSource() == IncidentPersonnelSource.DIVERA) {
                    diveraPersonIds.add(person.getId());
                }
                IncidentReportVehicle reportVehicle = row.getIncidentReportVehicle();
                if (reportVehicle != null && reportVehicle.getVehicle() != null) {
                    long vehicleId = reportVehicle.getVehicle().getId();
                    crewByVehicleId.computeIfAbsent(vehicleId, k -> new ArrayList<>()).add(person.getId());
                    onVehiclePersonIds.add(person.getId());
                }
            }
        }

        List<KraefteFahrzeugeState.KraeftePersonView> manualPersons = new ArrayList<>();
        List<KraefteFahrzeugeState.KraeftePersonView> diveraPersons = new ArrayList<>();

        for (Person person : allPersons) {
            KraefteFahrzeugeState.KraeftePersonView view = toPersonView(person);
            if (diveraPersonIds.contains(person.getId())) {
                if (!onVehiclePersonIds.contains(person.getId())) {
                    diveraPersons.add(view);
                }
            } else if (!onVehiclePersonIds.contains(person.getId())) {
                manualPersons.add(view);
            }
        }

        List<KraefteFahrzeugeState.KraefteVehicleView> vehicles = new ArrayList<>();
        for (Vehicle vehicle : unitVehicles) {
            List<Long> crewIds = crewByVehicleId.getOrDefault(vehicle.getId(), List.of());
            List<Person> crew = crewIds.stream()
                    .map(personById::get)
                    .filter(Objects::nonNull)
                    .toList();
            List<KraefteFahrzeugeState.KraeftePersonView> crewViews =
                    crew.stream().map(this::toPersonView).toList();
            vehicles.add(new KraefteFahrzeugeState.KraefteVehicleView(
                    vehicle.getId(),
                    vehicle.getName(),
                    new ArrayList<>(crewIds),
                    crewViews,
                    Besatzungsstaerke.format(crew)));
        }

        return new KraefteFahrzeugeState(manualPersons, diveraPersons, vehicles);
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
                result.add(new CrewAssignment(payload.vehicleId(), personIds));
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
        return saved;
    }

    @Transactional
    public IncidentReport update(long unitId, long reportId, EinsatzberichtFormData form, AppUserDetails actor) {
        validateRequired(form);
        IncidentReport report = requireReport(unitId, reportId);
        applyForm(report, form, unitId);
        IncidentReport saved = incidentReportRepository.save(report);
        saveCrewAssignments(saved, form, unitId);
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
        report.setIncidentTypeKey("SONSTIGES");
        report.setIncidentTypeLabel(stichwort.isEmpty() ? "Sonstiges" : stichwort);
        report.setLocation(form.location().trim());
        report.setPostalCode(trimToNull(form.postalCode()));
        report.setDistrict(null);
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

        for (CrewAssignment assignment : assignments) {
            IncidentReportVehicle reportVehicle = reportVehicleByUnitVehicleId.get(assignment.vehicleId());
            if (reportVehicle == null) {
                continue;
            }
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
                incidentReportPersonnelRepository.save(row);
            }
        }
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

    private KraefteFahrzeugeState.KraeftePersonView toPersonView(Person person) {
        return new KraefteFahrzeugeState.KraeftePersonView(
                person.getId(),
                person.anwesenheitDisplayName(),
                Besatzungsstaerke.qualTier(person).name());
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

    private record CrewAssignmentPayload(Long vehicleId, List<Long> personIds) {}
}
