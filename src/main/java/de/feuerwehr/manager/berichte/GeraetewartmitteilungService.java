package de.feuerwehr.manager.berichte;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.technik.Vehicle;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GeraetewartmitteilungService {

    private final EquipmentMaintenanceReportRepository reportRepository;
    private final IncidentReportEquipmentRepository incidentReportEquipmentRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final PersonRepository personRepository;
    private final PersonalService personalService;
    private final TestModeService testModeService;
    private final EinsatzberichtService einsatzberichtService;
    private final ObjectMapper objectMapper;
    private final @Lazy BerichteEmailNotificationService berichteEmailNotificationService;

    @Transactional(readOnly = true)
    public GeraetewartmitteilungListResponse listForYear(long unitId, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = yearStart.plusYears(1);
        List<GeraetewartmitteilungListItemView> items = reportRepository
                .findByUnitIdAndYear(unitId, yearStart, yearEnd, includeTestReports())
                .stream()
                .map(this::toListItem)
                .toList();
        return new GeraetewartmitteilungListResponse(items);
    }

    @Transactional(readOnly = true)
    public EquipmentMaintenanceReport requireReport(long unitId, long reportId) {
        return reportRepository
                .findByIdAndUnitId(reportId, unitId, includeTestReports())
                .orElseThrow(() -> new IllegalArgumentException("Gerätewartmitteilung nicht gefunden."));
    }

    public GeraetewartmitteilungForm newForm() {
        GeraetewartmitteilungForm form = new GeraetewartmitteilungForm();
        form.setEventDate(LocalDate.now());
        return form;
    }

    @Transactional(readOnly = true)
    public GeraetewartmitteilungForm toForm(EquipmentMaintenanceReport report) {
        GeraetewartmitteilungForm form = new GeraetewartmitteilungForm();
        form.setTyp(report.getTyp() != null ? report.getTyp().name() : GeraetewartTyp.UEBUNG.name());
        form.setEventArt(
                report.getEventArt() != null
                        ? report.getEventArt().name()
                        : GeraetewartEventArt.BRANDEINSATZ.name());
        form.setEventDate(report.getEventDate());
        form.setReadiness(
                report.getReadiness() != null
                        ? report.getReadiness().name()
                        : GeraetewartReadiness.HERGESTELLT.name());
        if (report.getLeaderPerson() != null) {
            form.setLeaderPersonId(report.getLeaderPerson().getId());
            form.setLeaderName(report.getLeaderPerson().anwesenheitDisplayName());
        } else {
            form.setLeaderName(report.getLeaderName());
        }
        List<GwmVehicleData> vehicles = parseVehicles(report);
        form.setVehiclesDataJson(GwmVehicleDataSupport.serialize(vehicles, objectMapper));
        form.setDeployedEquipmentJson(GwmVehicleDataSupport.toDeployedEquipmentJson(vehicles, objectMapper));
        return form;
    }

    @Transactional(readOnly = true)
    public String buildVehiclesJson(long unitId) {
        List<Map<String, Object>> items = einsatzberichtService.listVehiclesForForm(unitId).stream()
                .map(vehicle -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", vehicle.getId());
                    item.put("name", vehicle.getName());
                    return item;
                })
                .toList();
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Transactional
    public EquipmentMaintenanceReport create(long unitId, GeraetewartmitteilungForm form, AppUserDetails actor) {
        validateForm(form);
        EquipmentMaintenanceReport report = new EquipmentMaintenanceReport();
        report.setUnit(requireUnit(unitId));
        applyForm(report, form, unitId);
        report.setTestData(testModeService.isEnabled());
        applyCreator(report, actor);
        EquipmentMaintenanceReport saved = reportRepository.save(report);
        berichteEmailNotificationService.trySendOnCreate(unitId, BerichteEmailReportType.GERAETEWART, saved.getId());
        return saved;
    }

    @Transactional
    public EquipmentMaintenanceReport createFromIncidentReport(
            long unitId, long incidentReportId, AppUserDetails actor) {
        IncidentReport incident = einsatzberichtService.requireReport(unitId, incidentReportId);
        GeraetewartmitteilungForm form = buildFormFromIncidentReport(unitId, incidentReportId, incident);
        return create(unitId, form, actor);
    }

    @Transactional(readOnly = true)
    public GeraetewartmitteilungForm buildFormFromIncidentReport(
            long unitId, long incidentReportId, IncidentReport incident) {
        KraefteFahrzeugeState state = einsatzberichtService.buildKraefteFahrzeugeState(unitId, incidentReportId);
        Map<Long, List<Long>> equipmentByVehicleId = loadEquipmentByVehicleId(incidentReportId);
        Map<Long, KraefteFahrzeugeState.KraefteVehicleView> vehicleViewById = new LinkedHashMap<>();
        for (KraefteFahrzeugeState.KraefteVehicleView vehicle : state.vehicles()) {
            vehicleViewById.put(vehicle.vehicleId(), vehicle);
        }

        Set<Long> vehicleIds = new LinkedHashSet<>();
        for (KraefteFahrzeugeState.KraefteVehicleView vehicle : state.involvedVehicles()) {
            vehicleIds.add(vehicle.vehicleId());
        }
        vehicleIds.addAll(equipmentByVehicleId.keySet());
        if (vehicleIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Keine Fahrzeuge im Einsatzbericht – Gerätewartmitteilung kann nicht erstellt werden.");
        }

        List<GwmVehicleData> gwmVehicles = new ArrayList<>();
        String equipmentDamage = incident.getEquipmentDamage();
        boolean damageAssigned = false;
        for (Long vehicleId : vehicleIds) {
            KraefteFahrzeugeState.KraefteVehicleView view = vehicleViewById.get(vehicleId);
            List<Long> equipmentIds = equipmentByVehicleId.getOrDefault(vehicleId, List.of());
            String defectiveFreitext = null;
            if (!damageAssigned && equipmentDamage != null && !equipmentDamage.isBlank()) {
                defectiveFreitext = equipmentDamage.trim();
                damageAssigned = true;
            }
            gwmVehicles.add(new GwmVehicleData(
                    vehicleId,
                    view != null ? resolvePersonRef(view.maschinistPersonId()) : null,
                    view != null ? resolvePersonRef(view.einheitsfuehrerPersonId()) : null,
                    equipmentIds,
                    List.of(),
                    Map.of(),
                    defectiveFreitext,
                    null));
        }

        GeraetewartmitteilungForm form = new GeraetewartmitteilungForm();
        form.setTyp(GeraetewartTyp.EINSATZ.name());
        form.setEventArt(mapEventArt(incident).name());
        form.setEventDate(incident.getIncidentDate() != null ? incident.getIncidentDate() : LocalDate.now());
        form.setReadiness(GeraetewartReadiness.HERGESTELLT.name());
        if (incident.getCommanderPerson() != null) {
            form.setLeaderPersonId(incident.getCommanderPerson().getId());
        } else if (incident.getIncidentCommander() != null && !incident.getIncidentCommander().isBlank()) {
            form.setLeaderName(incident.getIncidentCommander().trim());
        }
        form.setVehiclesDataJson(GwmVehicleDataSupport.serialize(gwmVehicles, objectMapper));
        form.setDeployedEquipmentJson(GwmVehicleDataSupport.toDeployedEquipmentJson(gwmVehicles, objectMapper));
        return form;
    }

    @Transactional
    public EquipmentMaintenanceReport update(
            long unitId, long reportId, GeraetewartmitteilungForm form, AppUserDetails actor) {
        validateForm(form);
        EquipmentMaintenanceReport report = requireReport(unitId, reportId);
        if (!GeraetewartmitteilungAccess.canEdit(report, actor)) {
            throw new IllegalArgumentException("Diese Gerätewartmitteilung kann nicht bearbeitet werden.");
        }
        applyForm(report, form, unitId);
        return reportRepository.save(report);
    }

    @Transactional
    public void delete(long unitId, long reportId, AppUserDetails actor) {
        EquipmentMaintenanceReport report = requireReport(unitId, reportId);
        if (!GeraetewartmitteilungAccess.canDelete(report, actor)) {
            throw new IllegalArgumentException("Diese Gerätewartmitteilung kann nicht gelöscht werden.");
        }
        reportRepository.delete(report);
    }

    @Transactional(readOnly = true)
    public List<GeraetewartPdfVehicleRow> buildVehicleRows(long unitId, EquipmentMaintenanceReport report) {
        List<GwmVehicleData> vehicles = parseVehicles(report);
        if (vehicles.isEmpty()) {
            return List.of();
        }
        Map<Long, Vehicle> vehicleById = new LinkedHashMap<>();
        einsatzberichtService.listVehiclesForForm(unitId).forEach(v -> vehicleById.put(v.getId(), v));
        Map<Long, Person> personById = loadPersonsForVehicles(unitId, vehicles);
        List<GeraetewartPdfVehicleRow> rows = new ArrayList<>();
        for (GwmVehicleData vehicle : vehicles) {
            Vehicle entity = vehicleById.get(vehicle.vehicleId());
            String vehicleName = entity != null ? entity.getName() : "Fahrzeug " + vehicle.vehicleId();
            rows.add(new GeraetewartPdfVehicleRow(
                    vehicleName,
                    personDisplay(personById.get(vehicle.maschinistPersonId())),
                    personDisplay(personById.get(vehicle.einheitsfuehrerPersonId())),
                    resolveEquipmentNames(unitId, vehicle),
                    formatDefects(unitId, vehicle)));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public String resolveLeaderDisplay(EquipmentMaintenanceReport report) {
        if (report.getLeaderPerson() != null) {
            return report.getLeaderPerson().anwesenheitDisplayName();
        }
        if (report.getLeaderName() != null && !report.getLeaderName().isBlank()) {
            return report.getLeaderName().trim();
        }
        return "—";
    }

    public String leaderFieldLabel(GeraetewartTyp typ) {
        return typ == GeraetewartTyp.EINSATZ ? "Einsatzleiter" : "Übungsleiter";
    }

    public List<GwmVehicleData> parseVehicles(EquipmentMaintenanceReport report) {
        return GwmVehicleDataSupport.parse(
                report.getVehiclesDataJson(), report.getDeployedEquipmentJson(), objectMapper);
    }

    public List<GwmVehicleData> parseVehicles(GeraetewartmitteilungForm form) {
        List<GwmVehicleData> vehicles =
                GwmVehicleDataSupport.parseVehiclesJson(form.getVehiclesDataJson(), objectMapper);
        if (!vehicles.isEmpty()) {
            return vehicles;
        }
        return GwmVehicleDataSupport.parse(null, form.getDeployedEquipmentJson(), objectMapper);
    }

    private Map<Long, Person> loadPersonsForVehicles(long unitId, List<GwmVehicleData> vehicles) {
        Set<Long> personIds = new LinkedHashSet<>();
        for (GwmVehicleData vehicle : vehicles) {
            if (vehicle.maschinistPersonId() != null) {
                personIds.add(vehicle.maschinistPersonId());
            }
            if (vehicle.einheitsfuehrerPersonId() != null) {
                personIds.add(vehicle.einheitsfuehrerPersonId());
            }
        }
        Map<Long, Person> personById = new LinkedHashMap<>();
        if (!personIds.isEmpty()) {
            personRepository
                    .findActiveByIdIn(personIds, includeTestReports())
                    .forEach(person -> personById.put(person.getId(), person));
        }
        return personById;
    }

    private static String personDisplay(Person person) {
        return person != null ? person.anwesenheitDisplayName() : "—";
    }

    private List<String> resolveEquipmentNames(long unitId, GwmVehicleData vehicle) {
        if (vehicle.equipmentIds() == null || vehicle.equipmentIds().isEmpty()) {
            return List.of();
        }
        List<VehicleEquipmentView> equipmentViews =
                einsatzberichtService.listVehicleEquipment(unitId, List.of(vehicle.vehicleId()));
        if (equipmentViews.isEmpty()) {
            return List.of();
        }
        Map<Long, String> nameById = new LinkedHashMap<>();
        for (VehicleEquipmentView.EquipmentItemView item : equipmentViews.get(0).equipment()) {
            nameById.put(item.id(), item.name());
        }
        return vehicle.equipmentIds().stream()
                .map(nameById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private String formatDefects(long unitId, GwmVehicleData vehicle) {
        List<String> parts = new ArrayList<>();
        Map<Long, String> mangelByEquipment =
                vehicle.defectiveMangelByEquipmentId() != null
                        ? vehicle.defectiveMangelByEquipmentId()
                        : Map.of();
        if (vehicle.defectiveEquipmentIds() != null && !vehicle.defectiveEquipmentIds().isEmpty()) {
            List<VehicleEquipmentView> equipmentViews =
                    einsatzberichtService.listVehicleEquipment(unitId, List.of(vehicle.vehicleId()));
            Map<Long, String> nameById = new LinkedHashMap<>();
            if (!equipmentViews.isEmpty()) {
                for (VehicleEquipmentView.EquipmentItemView item : equipmentViews.get(0).equipment()) {
                    nameById.put(item.id(), item.name());
                }
            }
            for (Long equipmentId : vehicle.defectiveEquipmentIds()) {
                String name = nameById.get(equipmentId);
                if (name == null) {
                    continue;
                }
                String mangel = mangelByEquipment.get(equipmentId);
                if (mangel != null && !mangel.isBlank()) {
                    parts.add(name + " – " + mangel.trim());
                } else {
                    parts.add(name);
                }
            }
        }
        if (vehicle.defectiveFreitext() != null && !vehicle.defectiveFreitext().isBlank()) {
            String freitext = vehicle.defectiveFreitext().trim();
            if (vehicle.defectiveFreitextMangel() != null && !vehicle.defectiveFreitextMangel().isBlank()) {
                parts.add(freitext + " – " + vehicle.defectiveFreitextMangel().trim());
            } else {
                parts.add(freitext);
            }
        }
        return parts.isEmpty() ? "—" : String.join(", ", parts);
    }

    private void applyForm(EquipmentMaintenanceReport report, GeraetewartmitteilungForm form, long unitId) {
        report.setTyp(GeraetewartTyp.fromKey(form.getTyp()));
        report.setEventArt(GeraetewartEventArt.fromKey(form.getEventArt()));
        report.setEventDate(form.getEventDate());
        report.setReadiness(GeraetewartReadiness.fromKey(form.getReadiness()));
        List<GwmVehicleData> vehicles = parseVehicles(form);
        report.setVehiclesDataJson(GwmVehicleDataSupport.serialize(vehicles, objectMapper));
        report.setDeployedEquipmentJson(GwmVehicleDataSupport.toDeployedEquipmentJson(vehicles, objectMapper));
        applyLeader(report, form, unitId);
    }

    private void applyLeader(EquipmentMaintenanceReport report, GeraetewartmitteilungForm form, long unitId) {
        report.setLeaderPerson(null);
        report.setLeaderName(null);
        if (form.getLeaderPersonId() != null && form.getLeaderPersonId() > 0) {
            personRepository
                    .findActiveById(form.getLeaderPersonId(), includeTestReports())
                    .ifPresentOrElse(
                            report::setLeaderPerson,
                            () -> {
                                throw new IllegalArgumentException("Einsatz-/Übungsleiter nicht gefunden.");
                            });
            return;
        }
        if (form.getLeaderName() != null && !form.getLeaderName().isBlank()) {
            report.setLeaderName(form.getLeaderName().trim());
        }
    }

    private void validateForm(GeraetewartmitteilungForm form) {
        if (form == null) {
            throw new IllegalArgumentException("Bitte alle Pflichtfelder ausfüllen.");
        }
        if (form.getEventDate() == null) {
            throw new IllegalArgumentException("Bitte ein Datum angeben.");
        }
        if (parseVehicles(form).isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens ein Fahrzeug auswählen.");
        }
    }

    private void applyCreator(EquipmentMaintenanceReport report, AppUserDetails actor) {
        if (actor == null) {
            return;
        }
        userRepository.findById(actor.getUserId()).ifPresent(report::setCreatedByUser);
        if (actor.getDisplayName() != null && !actor.getDisplayName().isBlank()) {
            report.setCreatedByName(actor.getDisplayName().trim());
        }
    }

    private GeraetewartmitteilungListItemView toListItem(EquipmentMaintenanceReport report) {
        List<GwmVehicleData> vehicles = parseVehicles(report);
        Long creatorId = report.getCreatedByUser() != null ? report.getCreatedByUser().getId() : null;
        GeraetewartTyp typ = report.getTyp() != null ? report.getTyp() : GeraetewartTyp.UEBUNG;
        GeraetewartEventArt eventArt =
                report.getEventArt() != null ? report.getEventArt() : GeraetewartEventArt.BRANDEINSATZ;
        GeraetewartReadiness readiness =
                report.getReadiness() != null ? report.getReadiness() : GeraetewartReadiness.HERGESTELLT;
        return new GeraetewartmitteilungListItemView(
                report.getId(),
                report.getEventDate(),
                typ.name().toLowerCase(),
                typ.label(),
                eventArt.label(),
                readiness.name().toLowerCase(),
                readiness.label(),
                resolveLeaderDisplay(report),
                vehicles.size(),
                creatorId);
    }

    private Unit requireUnit(long unitId) {
        return unitRepository
                .findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
    }

    private boolean includeTestReports() {
        return testModeService.isEnabled();
    }

    private Map<Long, List<Long>> loadEquipmentByVehicleId(long incidentReportId) {
        Map<Long, List<Long>> equipmentByVehicleId = new LinkedHashMap<>();
        for (IncidentReportEquipment row : incidentReportEquipmentRepository.findByIncidentReportId(incidentReportId)) {
            if (row.getVehicle() == null || row.getVehicleEquipment() == null) {
                continue;
            }
            equipmentByVehicleId
                    .computeIfAbsent(row.getVehicle().getId(), ignored -> new ArrayList<>())
                    .add(row.getVehicleEquipment().getId());
        }
        return equipmentByVehicleId;
    }

    private static Long resolvePersonRef(Long refId) {
        if (refId == null || IncidentPersonnelRefs.isUcrRef(refId)) {
            return null;
        }
        return refId;
    }

    private static GeraetewartEventArt mapEventArt(IncidentReport incident) {
        String text = ((incident.getStichwort() != null ? incident.getStichwort() : "") + " "
                        + (incident.getIncidentTypeLabel() != null ? incident.getIncidentTypeLabel() : ""))
                .toUpperCase();
        if (text.contains("CBRN")) {
            return GeraetewartEventArt.CBRN;
        }
        if (text.contains("TECH") || text.contains("THL")) {
            return GeraetewartEventArt.TECH_HILFE;
        }
        if (text.contains("BRAND")) {
            return GeraetewartEventArt.BRANDEINSATZ;
        }
        return GeraetewartEventArt.SONSTIGES;
    }

    public record GeraetewartPdfVehicleRow(
            String vehicleName,
            String maschinistDisplay,
            String einheitsfuehrerDisplay,
            List<String> equipmentNames,
            String defectsDisplay) {}
}
