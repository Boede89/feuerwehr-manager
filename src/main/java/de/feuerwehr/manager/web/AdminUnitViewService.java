package de.feuerwehr.manager.web;

import de.feuerwehr.manager.personal.Course;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.personal.QualificationType;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.technik.Vehicle;
import de.feuerwehr.manager.technik.VehicleEquipment;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitAdminService;
import de.feuerwehr.manager.unit.UnitCalendarSettings;
import de.feuerwehr.manager.unit.UnitDiveraSettings;
import de.feuerwehr.manager.unit.UnitDiveraSettingsRepository;
import de.feuerwehr.manager.unit.UnitRole;
import de.feuerwehr.manager.unit.UnitRolePermission;
import de.feuerwehr.manager.unit.UnitRoleService;
import de.feuerwehr.manager.unit.UnitSmtpSettings;
import de.feuerwehr.manager.unit.UnitRoleType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

@Service
@RequiredArgsConstructor
public class AdminUnitViewService {

    private final UnitAdminService unitAdminService;
    private final UnitRoleService unitRoleService;
    private final UnitDiveraSettingsRepository diveraSettingsRepository;
    private final ModuleSettingsService moduleSettingsService;
    private final PersonalService personalService;

    public void populateKonfiguration(Model model, Unit unit) {
        model.addAttribute("unit", unit);
        model.addAttribute("hasUnitLogo", unit.getLogoBase64() != null && !unit.getLogoBase64().isBlank());
    }

    public void populateRollen(Model model, long unitId) {
        unitRoleService.ensureSystemRoles(unitId);
        List<UnitRole> roles = unitRoleService.listRoles(unitId);
        model.addAttribute("unitRoles", roles);
        model.addAttribute("rolePermissionOptions", UnitRolePermission.permissionOptions());
        model.addAttribute("roleTypes", UnitRoleType.values());
        Map<Long, String> permissionLabelsByRoleId = new HashMap<>();
        for (UnitRole role : roles) {
            permissionLabelsByRoleId.put(role.getId(), unitRoleService.formatPermissionsLabel(role));
        }
        model.addAttribute("permissionLabelsByRoleId", permissionLabelsByRoleId);
    }

    public void populateSchnittstellen(Model model, long unitId) {
        UnitSmtpSettings smtp = unitAdminService.getOrCreateSmtp(unitId);
        UnitCalendarSettings calendar = unitAdminService.getOrCreateCalendar(unitId);
        model.addAttribute("unitSmtp", smtp);
        model.addAttribute("unitCalendar", calendar);
        model.addAttribute("smtpPasswordConfigured", unitAdminService.isSmtpPasswordConfigured(unitId));
        populateDivera(model, unitId);
    }

    public void populateModule(Model model, long unitId) {
        model.addAttribute("modulesEnabled", moduleSettingsService.modulesEnabled(unitId));
        model.addAttribute("moduleDefs", Arrays.asList(AppModule.values()));
    }

    public void populateTechnik(Model model, long unitId) {
        List<Vehicle> vehicles = unitAdminService.listVehicles(unitId);
        model.addAttribute("vehicles", vehicles);
        model.addAttribute("rooms", unitAdminService.listRooms(unitId));
        Long resolvedVehicleId = null;
        Object param = model.getAttribute("selectedVehicleId");
        if (param instanceof Long id) {
            resolvedVehicleId = id;
        }
        if (resolvedVehicleId == null && !vehicles.isEmpty()) {
            resolvedVehicleId = vehicles.get(0).getId();
        }
        final Long selectedVehicleId = resolvedVehicleId;
        model.addAttribute("selectedVehicleId", selectedVehicleId);
        if (selectedVehicleId != null) {
            model.addAttribute("equipmentCategories", unitAdminService.listEquipmentCategories(selectedVehicleId));
            model.addAttribute("equipmentItems", unitAdminService.listEquipment(selectedVehicleId));
            vehicles.stream()
                    .filter(v -> v.getId().equals(selectedVehicleId))
                    .findFirst()
                    .ifPresent(v -> model.addAttribute("selectedVehicleName", v.getName()));
        }
    }

    public void populateAusbildung(Model model, long unitId) {
        List<QualificationType> types = personalService.listQualificationTypes(unitId, false);
        List<Course> courses = personalService.listCourses(unitId, false);
        model.addAttribute("qualificationTypes", types);
        model.addAttribute("courses", courses);
    }

    private void populateDivera(Model model, long unitId) {
        Optional<UnitDiveraSettings> opt = diveraSettingsRepository.findByUnitId(unitId);
        if (opt.isPresent()) {
            UnitDiveraSettings s = opt.get();
            model.addAttribute("apiBaseUrl", s.getApiBaseUrl());
            model.addAttribute("accessKeyConfigured", s.getAccessKey() != null && !s.getAccessKey().isBlank());
        } else {
            model.addAttribute("apiBaseUrl", "https://app.divera247.com");
            model.addAttribute("accessKeyConfigured", false);
        }
    }
}
