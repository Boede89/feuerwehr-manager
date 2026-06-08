package de.feuerwehr.manager.berichte;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EinsatzberichtService {

    private final IncidentReportRepository incidentReportRepository;
    private final IncidentReportPersonnelRepository incidentReportPersonnelRepository;
    private final IncidentReportVehicleRepository incidentReportVehicleRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final PersonalService personalService;
    private final VehicleRepository vehicleRepository;
    private final TestModeService testModeService;

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

    public List<Long> selectedPersonnelIds(long reportId) {
        return incidentReportPersonnelRepository.findByIncidentReportId(reportId).stream()
                .map(IncidentReportPersonnel::getPerson)
                .filter(Objects::nonNull)
                .map(Person::getId)
                .toList();
    }

    public List<Long> selectedVehicleIds(long reportId) {
        return incidentReportVehicleRepository.findByIncidentReportId(reportId).stream()
                .map(IncidentReportVehicle::getVehicle)
                .filter(Objects::nonNull)
                .map(Vehicle::getId)
                .toList();
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
        saveAssignments(saved, form, unitId);
        return saved;
    }

    @Transactional
    public IncidentReport update(long unitId, long reportId, EinsatzberichtFormData form, AppUserDetails actor) {
        validateRequired(form);
        IncidentReport report = requireReport(unitId, reportId);
        applyForm(report, form, unitId);
        IncidentReport saved = incidentReportRepository.save(report);
        saveAssignments(saved, form, unitId);
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
        int personnelCount = form.personnelPersonIds() != null ? form.personnelPersonIds().size() : 0;
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
        Long commanderId = form.commanderPersonId();
        if (commanderId == null) {
            report.setCommanderPerson(null);
            report.setIncidentCommander(trimToNull(form.incidentCommander()));
            return;
        }
        Person commander = resolvePersonForUnit(commanderId, unitId);
        report.setCommanderPerson(commander);
        report.setIncidentCommander(commander.anwesenheitDisplayName());
    }

    private void saveAssignments(IncidentReport report, EinsatzberichtFormData form, long unitId) {
        long reportId = report.getId();
        incidentReportPersonnelRepository.deleteByIncidentReportId(reportId);
        incidentReportVehicleRepository.deleteByIncidentReportId(reportId);

        Set<Long> personIds = new LinkedHashSet<>();
        if (form.personnelPersonIds() != null) {
            personIds.addAll(form.personnelPersonIds());
        }
        for (Long personId : personIds) {
            if (personId == null) {
                continue;
            }
            Person person = resolvePersonForUnit(personId, unitId);
            IncidentReportPersonnel row = new IncidentReportPersonnel();
            row.setIncidentReport(report);
            row.setPerson(person);
            row.setDisplayName(person.anwesenheitDisplayName());
            incidentReportPersonnelRepository.save(row);
        }

        Set<Long> vehicleIdSet = new LinkedHashSet<>();
        if (form.vehicleIds() != null) {
            vehicleIdSet.addAll(form.vehicleIds());
        }
        for (Long vehicleId : vehicleIdSet) {
            if (vehicleId == null) {
                continue;
            }
            Vehicle vehicle = vehicleRepository
                    .findByIdAndUnitId(vehicleId, unitId)
                    .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden."));
            IncidentReportVehicle row = new IncidentReportVehicle();
            row.setIncidentReport(report);
            row.setVehicle(vehicle);
            row.setVehicleName(vehicle.getName());
            incidentReportVehicleRepository.save(row);
        }
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
}
