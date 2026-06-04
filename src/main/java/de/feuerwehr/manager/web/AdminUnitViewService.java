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
import de.feuerwehr.manager.unit.UnitCalendarAccount;
import de.feuerwehr.manager.unit.UnitDiveraSettings;
import de.feuerwehr.manager.unit.UnitSmtpAccount;
import de.feuerwehr.manager.divera.DiveraIntegrationSupport;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.unit.UnitDiveraSettingsRepository;
import de.feuerwehr.manager.unit.UnitRole;
import de.feuerwehr.manager.unit.UnitRolePermission;
import de.feuerwehr.manager.unit.UnitRoleService;
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
    private final GlobalSettingsService globalSettingsService;
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
        List<UnitSmtpAccount> smtpAccounts = unitAdminService.listSmtpAccounts(unitId);
        List<UnitCalendarAccount> calendarAccounts = unitAdminService.listCalendarAccounts(unitId);
        model.addAttribute("smtpAccounts", smtpAccounts);
        model.addAttribute("calendarAccounts", calendarAccounts);
        Map<Long, Boolean> smtpPasswordConfigured = new HashMap<>();
        for (UnitSmtpAccount a : smtpAccounts) {
            smtpPasswordConfigured.put(a.getId(), unitAdminService.isSmtpPasswordConfigured(a));
        }
        model.addAttribute("smtpPasswordConfigured", smtpPasswordConfigured);
        Map<Long, Boolean> calendarCredentialsConfigured = new HashMap<>();
        for (UnitCalendarAccount a : calendarAccounts) {
            calendarCredentialsConfigured.put(a.getId(), unitAdminService.isCalendarCredentialsConfigured(a));
        }
        model.addAttribute("calendarCredentialsConfigured", calendarCredentialsConfigured);
        String appBase = globalSettingsService.get().getAppUrl();
        model.addAttribute("appBaseUrl", appBase != null ? appBase : "");
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
        model.addAttribute("equipmentCountByVehicleId", unitAdminService.equipmentCountByVehicleId(unitId));
        Long selectedVehicleId = null;
        Object param = model.getAttribute("selectedVehicleId");
        if (param instanceof Long id) {
            selectedVehicleId = id;
        }
        model.addAttribute("selectedVehicleId", selectedVehicleId);
        model.addAttribute("openEquipmentModal", selectedVehicleId != null);
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
        String appBase = globalSettingsService.get().getAppUrl();
        Optional<UnitDiveraSettings> opt = diveraSettingsRepository.findByUnitId(unitId);
        if (opt.isPresent()) {
            UnitDiveraSettings s = opt.get();
            model.addAttribute("accessKeyConfigured", s.getAccessKey() != null && !s.getAccessKey().isBlank());
            model.addAttribute(
                    "webhookSecretConfigured",
                    s.getWebhookSecret() != null && !s.getWebhookSecret().isBlank());
            model.addAttribute("diveraWebhookUrl", DiveraIntegrationSupport.webhookUrlForSettings(appBase, s));
        } else {
            model.addAttribute("accessKeyConfigured", false);
            model.addAttribute("webhookSecretConfigured", false);
            model.addAttribute(
                    "diveraWebhookUrl",
                    DiveraIntegrationSupport.buildWebhookUrl(appBase, unitId, null));
        }
    }
}
