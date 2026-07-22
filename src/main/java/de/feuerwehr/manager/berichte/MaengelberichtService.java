package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.technik.Vehicle;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaengelberichtService {

    private final DefectReportRepository reportRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final PersonRepository personRepository;
    private final EinsatzberichtService einsatzberichtService;
    private final TestModeService testModeService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public MaengelberichtListResponse listForYear(long unitId, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = yearStart.plusYears(1);
        List<MaengelberichtListItemView> items = reportRepository
                .findByUnitIdAndYear(unitId, yearStart, yearEnd, includeTestReports())
                .stream()
                .map(this::toListItem)
                .toList();
        return new MaengelberichtListResponse(items);
    }

    @Transactional(readOnly = true)
    public DefectReport requireReport(long unitId, long reportId) {
        return reportRepository
                .findByIdAndUnitId(reportId, unitId, includeTestReports())
                .orElseThrow(() -> new IllegalArgumentException("Mängelbericht nicht gefunden."));
    }

    public MaengelberichtForm newForm() {
        MaengelberichtForm form = new MaengelberichtForm();
        form.setAufgenommenAm(LocalDate.now());
        return form;
    }

    @Transactional(readOnly = true)
    public MaengelberichtForm toForm(DefectReport report) {
        MaengelberichtForm form = new MaengelberichtForm();
        form.setStandort(
                report.getStandort() != null ? report.getStandort().name() : MaengelberichtStandort.GH_AMERN.name());
        form.setMangelAn(
                report.getMangelAn() != null ? report.getMangelAn().name() : MaengelberichtMangelAn.GEBAEUDE.name());
        form.setBezeichnung(report.getBezeichnung());
        form.setMangelBeschreibung(report.getMangelBeschreibung());
        form.setUrsache(report.getUrsache());
        form.setVerbleib(report.getVerbleib());
        form.setAufgenommenAm(report.getAufgenommenAm());
        if (report.getVehicle() != null) {
            form.setVehicleId(report.getVehicle().getId());
        }
        if (report.getRecordedPerson() != null) {
            form.setRecordedPersonId(report.getRecordedPerson().getId());
            form.setRecordedByName(report.getRecordedPerson().anwesenheitDisplayName());
        } else {
            form.setRecordedByName(report.getRecordedByText());
        }
        return form;
    }

    @Transactional(readOnly = true)
    public List<Vehicle> listVehicles(long unitId) {
        return einsatzberichtService.listVehiclesForForm(unitId);
    }

    @Transactional
    public List<DefectReport> createFromIncidentReport(
            long unitId, long incidentReportId, AppUserDetails actor) {
        IncidentReport incident = einsatzberichtService.requireReport(unitId, incidentReportId);
        List<MaterialDamageEntry> entries = MaterialDamageEntriesSupport.parse(
                        incident.getMaterialDamageEntriesJson())
                .normalized()
                .entries();
        if (entries.isEmpty()) {
            return List.of();
        }
        List<DefectReport> created = new ArrayList<>();
        for (MaterialDamageEntry entry : entries) {
            created.add(create(unitId, toMaengelFormFromIncident(incident, entry, actor), actor));
        }
        return created;
    }

    @Transactional
    public List<DefectReport> createFromAttendanceReport(
            long unitId, AttendanceReport attendance, AppUserDetails actor) {
        if (attendance == null) {
            return List.of();
        }
        List<MaterialDamageEntry> entries = MaterialDamageEntriesSupport.parse(
                        attendance.getMaterialDamageEntriesJson())
                .normalized()
                .entries();
        if (entries.isEmpty()) {
            return List.of();
        }
        List<DefectReport> created = new ArrayList<>();
        for (MaterialDamageEntry entry : entries) {
            created.add(create(unitId, toMaengelFormFromAttendance(attendance, entry, actor), actor));
        }
        return created;
    }

    private MaengelberichtForm toMaengelFormFromAttendance(
            AttendanceReport attendance, MaterialDamageEntry entry, AppUserDetails actor) {
        MaengelberichtForm form = new MaengelberichtForm();
        form.setStandort(MaengelberichtStandort.GH_AMERN.name());
        form.setMangelAn(MaengelberichtMangelAn.fromKey(entry.mangelAn()).name());
        form.setBezeichnung(entry.bezeichnung());
        if (entry.vehicleId() != null && entry.vehicleId() > 0) {
            form.setVehicleId(entry.vehicleId());
        }
        form.setMangelBeschreibung(entry.mangelBeschreibung());
        form.setUrsache(entry.ursache());
        form.setVerbleib(entry.verbleib());
        form.setAufgenommenAm(attendance.getEventDate() != null ? attendance.getEventDate() : LocalDate.now());
        if (attendance.getInstructorResponsible() != null && !attendance.getInstructorResponsible().isBlank()) {
            form.setRecordedByName(attendance.getInstructorResponsible().trim());
        } else if (actor != null && actor.getDisplayName() != null && !actor.getDisplayName().isBlank()) {
            form.setRecordedByName(actor.getDisplayName().trim());
        }
        return form;
    }

    private MaengelberichtForm toMaengelFormFromIncident(
            IncidentReport incident, MaterialDamageEntry entry, AppUserDetails actor) {
        MaengelberichtForm form = new MaengelberichtForm();
        form.setStandort(MaengelberichtStandort.GH_AMERN.name());
        form.setMangelAn(MaengelberichtMangelAn.fromKey(entry.mangelAn()).name());
        form.setBezeichnung(entry.bezeichnung());
        if (entry.vehicleId() != null && entry.vehicleId() > 0) {
            form.setVehicleId(entry.vehicleId());
        }
        form.setMangelBeschreibung(entry.mangelBeschreibung());
        form.setUrsache(entry.ursache());
        form.setVerbleib(entry.verbleib());
        form.setAufgenommenAm(incident.getIncidentDate() != null ? incident.getIncidentDate() : LocalDate.now());
        if (incident.getCommanderPerson() != null) {
            form.setRecordedPersonId(incident.getCommanderPerson().getId());
            form.setRecordedByName(incident.getCommanderPerson().anwesenheitDisplayName());
        } else if (incident.getIncidentCommander() != null && !incident.getIncidentCommander().isBlank()) {
            form.setRecordedByName(incident.getIncidentCommander().trim());
        } else if (actor != null && actor.getDisplayName() != null && !actor.getDisplayName().isBlank()) {
            form.setRecordedByName(actor.getDisplayName().trim());
        }
        return form;
    }

    @Transactional
    public DefectReport create(long unitId, MaengelberichtForm form, AppUserDetails actor) {
        validateForm(form);
        DefectReport report = new DefectReport();
        report.setUnit(requireUnit(unitId));
        applyForm(report, form, unitId);
        report.setTestData(testModeService.isEnabled());
        applyCreator(report, actor);
        DefectReport saved = reportRepository.save(report);
        eventPublisher.publishEvent(BerichteEmailEvent.onCreate(unitId, BerichteEmailReportType.MAENGEL, saved.getId()));
        return saved;
    }

    @Transactional
    public DefectReport update(long unitId, long reportId, MaengelberichtForm form, AppUserDetails actor) {
        validateForm(form);
        DefectReport report = requireReport(unitId, reportId);
        if (!MaengelberichtAccess.canEdit(report, actor)) {
            throw new IllegalArgumentException("Dieser Mängelbericht kann nicht bearbeitet werden.");
        }
        applyForm(report, form, unitId);
        return reportRepository.save(report);
    }

    @Transactional
    public void delete(long unitId, long reportId, AppUserDetails actor) {
        DefectReport report = requireReport(unitId, reportId);
        if (!MaengelberichtAccess.canDelete(report, actor)) {
            throw new IllegalArgumentException("Dieser Mängelbericht kann nicht gelöscht werden.");
        }
        reportRepository.delete(report);
    }

    @Transactional(readOnly = true)
    public String resolveRecordedByDisplay(DefectReport report) {
        if (report.getRecordedPerson() != null) {
            return report.getRecordedPerson().anwesenheitDisplayName();
        }
        if (report.getRecordedByText() != null && !report.getRecordedByText().isBlank()) {
            return report.getRecordedByText().trim();
        }
        return "—";
    }

    @Transactional(readOnly = true)
    public String resolveVehicleDisplay(DefectReport report) {
        if (report.getVehicle() == null) {
            return "—";
        }
        return report.getVehicle().getName();
    }

    private void applyForm(DefectReport report, MaengelberichtForm form, long unitId) {
        report.setStandort(MaengelberichtStandort.fromKey(form.getStandort()));
        report.setMangelAn(MaengelberichtMangelAn.fromKey(form.getMangelAn()));
        report.setBezeichnung(trimOrNull(form.getBezeichnung()));
        report.setMangelBeschreibung(trimOrNull(form.getMangelBeschreibung()));
        report.setUrsache(trimOrNull(form.getUrsache()));
        report.setVerbleib(trimOrNull(form.getVerbleib()));
        report.setAufgenommenAm(form.getAufgenommenAm());
        applyVehicle(report, form.getVehicleId(), unitId);
        applyRecordedBy(report, form, unitId);
    }

    private void applyVehicle(DefectReport report, Long vehicleId, long unitId) {
        report.setVehicle(null);
        if (vehicleId == null || vehicleId <= 0) {
            return;
        }
        Map<Long, Vehicle> vehicleById = listVehicles(unitId).stream()
                .collect(Collectors.toMap(Vehicle::getId, v -> v, (a, b) -> a));
        Vehicle vehicle = vehicleById.get(vehicleId);
        if (vehicle == null) {
            throw new IllegalArgumentException("Fahrzeug nicht gefunden.");
        }
        report.setVehicle(vehicle);
    }

    private void applyRecordedBy(DefectReport report, MaengelberichtForm form, long unitId) {
        report.setRecordedPerson(null);
        report.setRecordedByText(null);
        if (form.getRecordedPersonId() != null && form.getRecordedPersonId() > 0) {
            personRepository
                    .findActiveById(form.getRecordedPersonId(), includeTestReports())
                    .ifPresentOrElse(
                            report::setRecordedPerson,
                            () -> {
                                throw new IllegalArgumentException("Aufgenommen durch: Person nicht gefunden.");
                            });
            return;
        }
        if (form.getRecordedByName() != null && !form.getRecordedByName().isBlank()) {
            report.setRecordedByText(form.getRecordedByName().trim());
        }
    }

    private void validateForm(MaengelberichtForm form) {
        if (form == null) {
            throw new IllegalArgumentException("Bitte alle Pflichtfelder ausfüllen.");
        }
        if (form.getAufgenommenAm() == null) {
            throw new IllegalArgumentException("Bitte ein Aufnahmedatum angeben.");
        }
        if (form.getRecordedPersonId() == null
                && (form.getRecordedByName() == null || form.getRecordedByName().isBlank())) {
            throw new IllegalArgumentException("Bitte „Aufgenommen durch“ angeben.");
        }
    }

    private void applyCreator(DefectReport report, AppUserDetails actor) {
        if (actor == null) {
            return;
        }
        userRepository.findById(actor.getUserId()).ifPresent(report::setCreatedByUser);
        if (actor.getDisplayName() != null && !actor.getDisplayName().isBlank()) {
            report.setCreatedByName(actor.getDisplayName().trim());
        }
    }

    private MaengelberichtListItemView toListItem(DefectReport report) {
        MaengelberichtStandort standort =
                report.getStandort() != null ? report.getStandort() : MaengelberichtStandort.GH_AMERN;
        MaengelberichtMangelAn mangelAn =
                report.getMangelAn() != null ? report.getMangelAn() : MaengelberichtMangelAn.GEBAEUDE;
        Long creatorId = report.getCreatedByUser() != null ? report.getCreatedByUser().getId() : null;
        return new MaengelberichtListItemView(
                report.getId(),
                report.getAufgenommenAm(),
                standort.label(),
                mangelAn.label(),
                report.getBezeichnung(),
                resolveRecordedByDisplay(report),
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

    private static String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
