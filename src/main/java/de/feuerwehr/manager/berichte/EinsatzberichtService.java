package de.feuerwehr.manager.berichte;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EinsatzberichtService {

    private static final TypeReference<Map<String, Object>> RESOURCE_MAP_TYPE = new TypeReference<>() {};

    private final IncidentReportRepository incidentReportRepository;
    private final IncidentTypeRepository incidentTypeRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final TestModeService testModeService;
    private final ObjectMapper objectMapper;

    public List<IncidentReport> listByUnit(long unitId) {
        return incidentReportRepository.findByUnitIdOrderByDateDesc(unitId);
    }

    public List<IncidentType> listActiveIncidentTypes() {
        return incidentTypeRepository.findByActiveTrueOrderByCategoryAscSortOrderAscLabelAsc();
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
        form.setIncidentTypeKey("SONSTIGES");
        form.setIncidentTypeLabel("Sonstiges");
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
        applyForm(report, form);
        report.setStatus(IncidentReportStatus.ENTWURF);
        report.setIncidentNumber(resolveIncidentNumber(unitId, form.incidentDate()));
        applyCreator(report, actor);
        return incidentReportRepository.save(report);
    }

    @Transactional
    public IncidentReport update(long unitId, long reportId, EinsatzberichtFormData form, AppUserDetails actor) {
        validateRequired(form);
        IncidentReport report = requireReport(unitId, reportId);
        applyForm(report, form);
        return incidentReportRepository.save(report);
    }

    public Map<String, Object> parseResources(IncidentReport report) {
        if (report.getResourcesJson() == null || report.getResourcesJson().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(report.getResourcesJson(), RESOURCE_MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    public Map<String, String> resourcesFromParams(Map<String, String> params) {
        Map<String, String> resources = new LinkedHashMap<>();
        for (IncidentResourceField field : IncidentResourceField.ALL) {
            String key = "res_" + field.key();
            String value = params.get(key);
            if (value != null && !value.isBlank() && !"0".equals(value.trim())) {
                resources.put(field.key(), value.trim());
            }
        }
        return resources;
    }

    private void applyForm(IncidentReport report, EinsatzberichtFormData form) {
        report.setIncidentDate(form.incidentDate());
        report.setAlarmTime(form.alarmTime());
        report.setDepartureTime(null);
        report.setArrivalTime(null);
        report.setEndTime(form.endTime());
        report.setIncidentTypeKey(form.incidentTypeKey());
        report.setIncidentTypeLabel(form.incidentTypeLabel());
        report.setLocation(form.location().trim());
        report.setPostalCode(trimToNull(form.postalCode()));
        report.setDistrict(null);
        report.setStreet(trimToNull(form.street()));
        report.setHouseNumber(trimToNull(form.houseNumber()));
        report.setExtinguishedBeforeArrival(form.extinguishedBeforeArrival());
        report.setMaliciousAlarm(form.maliciousAlarm());
        report.setFalseAlarm(form.falseAlarm());
        report.setSupraregional(form.supraregional());
        report.setBfInvolved(form.bfInvolved());
        report.setViolenceAgainstCrew(form.violenceAgainstCrew());
        report.setViolenceCount(Math.max(0, form.violenceCount()));
        report.setIncidentCommander(trimToNull(form.incidentCommander()));
        report.setReporterName(trimToNull(form.reporterName()));
        report.setReporterPhone(trimToNull(form.reporterPhone()));
        report.setStrengthLeadership(Math.max(0, form.strengthLeadership()));
        report.setStrengthSub(Math.max(0, form.strengthSub()));
        report.setStrengthCrew(Math.max(0, form.strengthCrew()));
        report.setFireObject(trimToNull(form.fireObject()));
        report.setSituation(trimToNull(form.situation()));
        report.setMeasures(trimToNull(form.measures()));
        report.setNotes(trimToNull(form.notes()));
        report.setWeatherInfluence(trimToNull(form.weatherInfluence()));
        report.setHandoverTo(trimToNull(form.handoverTo()));
        report.setHandoverNotes(trimToNull(form.handoverNotes()));
        report.setPoliceCaseNumber(trimToNull(form.policeCaseNumber()));
        report.setPoliceStation(trimToNull(form.policeStation()));
        report.setPoliceOfficer(trimToNull(form.policeOfficer()));
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
        report.setResourcesJson(writeResources(form.resources()));
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
        if (form.incidentTypeKey() == null || form.incidentTypeKey().isBlank()) {
            throw new IllegalArgumentException("Stichwort ist Pflichtfeld.");
        }
    }

    private String writeResources(Map<String, String> resources) {
        try {
            return objectMapper.writeValueAsString(resources != null ? resources : Map.of());
        } catch (Exception e) {
            return "{}";
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
