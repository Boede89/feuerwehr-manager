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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GeraetewartmitteilungService {

    private final EquipmentMaintenanceReportRepository reportRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final PersonRepository personRepository;
    private final PersonalService personalService;
    private final TestModeService testModeService;
    private final EinsatzberichtService einsatzberichtService;
    private final ObjectMapper objectMapper;

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
        form.setDeployedEquipmentJson(
                report.getDeployedEquipmentJson() != null && !report.getDeployedEquipmentJson().isBlank()
                        ? report.getDeployedEquipmentJson()
                        : "[]");
        return form;
    }

    @Transactional(readOnly = true)
    public String buildUnitPersonsJson(long unitId) {
        List<Map<String, Object>> items = personalService.listPersons(unitId).stream()
                .map(person -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", person.getId());
                    item.put("name", person.anwesenheitDisplayName());
                    return item;
                })
                .toList();
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
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
        return reportRepository.save(report);
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
        List<DeployedEquipmentAssignment> assignments =
                einsatzberichtService.parseDeployedEquipment(report.getDeployedEquipmentJson());
        if (assignments.isEmpty()) {
            return List.of();
        }
        Map<Long, Vehicle> vehicleById = new LinkedHashMap<>();
        einsatzberichtService.listVehiclesForForm(unitId).forEach(v -> vehicleById.put(v.getId(), v));
        List<GeraetewartPdfVehicleRow> rows = new ArrayList<>();
        for (DeployedEquipmentAssignment assignment : assignments) {
            Vehicle vehicle = vehicleById.get(assignment.vehicleId());
            String vehicleName = vehicle != null ? vehicle.getName() : "Fahrzeug " + assignment.vehicleId();
            List<String> equipmentNames = resolveEquipmentNames(unitId, assignment);
            rows.add(new GeraetewartPdfVehicleRow(vehicleName, equipmentNames));
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

    private List<String> resolveEquipmentNames(long unitId, DeployedEquipmentAssignment assignment) {
        if (assignment.equipmentIds() == null || assignment.equipmentIds().isEmpty()) {
            return List.of();
        }
        List<VehicleEquipmentView> equipmentViews =
                einsatzberichtService.listVehicleEquipment(unitId, List.of(assignment.vehicleId()));
        if (equipmentViews.isEmpty()) {
            return List.of();
        }
        Map<Long, String> nameById = new LinkedHashMap<>();
        for (VehicleEquipmentView.EquipmentItemView item : equipmentViews.get(0).equipment()) {
            nameById.put(item.id(), item.name());
        }
        return assignment.equipmentIds().stream()
                .map(nameById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private void applyForm(EquipmentMaintenanceReport report, GeraetewartmitteilungForm form, long unitId) {
        report.setTyp(GeraetewartTyp.fromKey(form.getTyp()));
        report.setEventDate(form.getEventDate());
        report.setReadiness(GeraetewartReadiness.fromKey(form.getReadiness()));
        report.setDeployedEquipmentJson(normalizeJson(form.getDeployedEquipmentJson()));
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
        List<DeployedEquipmentAssignment> assignments =
                einsatzberichtService.parseDeployedEquipment(form.getDeployedEquipmentJson());
        if (assignments.isEmpty()) {
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
        List<DeployedEquipmentAssignment> assignments =
                einsatzberichtService.parseDeployedEquipment(report.getDeployedEquipmentJson());
        Long creatorId = report.getCreatedByUser() != null ? report.getCreatedByUser().getId() : null;
        GeraetewartTyp typ = report.getTyp() != null ? report.getTyp() : GeraetewartTyp.UEBUNG;
        GeraetewartReadiness readiness =
                report.getReadiness() != null ? report.getReadiness() : GeraetewartReadiness.HERGESTELLT;
        return new GeraetewartmitteilungListItemView(
                report.getId(),
                report.getEventDate(),
                typ.name().toLowerCase(),
                typ.label(),
                readiness.name().toLowerCase(),
                readiness.label(),
                resolveLeaderDisplay(report),
                assignments.size(),
                creatorId);
    }

    private Unit requireUnit(long unitId) {
        return unitRepository
                .findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
    }

    private static String normalizeJson(String value) {
        if (value == null || value.isBlank()) {
            return "[]";
        }
        return value.trim();
    }

    private boolean includeTestReports() {
        return testModeService.isEnabled();
    }

    public record GeraetewartPdfVehicleRow(String vehicleName, List<String> equipmentNames) {}
}
