package de.feuerwehr.manager.web;

import de.feuerwehr.manager.personal.Course;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.personal.QualificationType;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.technik.EquipmentRow;
import de.feuerwehr.manager.technik.ChecklistInterval;
import de.feuerwehr.manager.technik.Vehicle;
import de.feuerwehr.manager.technik.VehicleChecklistService;
import de.feuerwehr.manager.technik.VehicleEquipment;
import de.feuerwehr.manager.technik.VehicleServiceStatus;
import de.feuerwehr.manager.technik.UnitVehicleTypeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitAdminService;
import de.feuerwehr.manager.unit.UnitCalendarAccount;
import de.feuerwehr.manager.unit.UnitDiveraSettings;
import de.feuerwehr.manager.unit.UnitSmtpAccount;
import de.feuerwehr.manager.print.PrintMode;
import de.feuerwehr.manager.print.UnitPrintSettingsService;
import de.feuerwehr.manager.unit.UnitPrintSettings;
import de.feuerwehr.manager.divera.DiveraIntegrationSupport;
import de.feuerwehr.manager.divera.DiveraMappingService;
import de.feuerwehr.manager.einsatzapp.EinsatzAppSettingsService;
import de.feuerwehr.manager.einsatzapp.FcmConfigService;
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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final UnitVehicleTypeService unitVehicleTypeService;
    private final VehicleChecklistService vehicleChecklistService;
    private final DiveraMappingService diveraMappingService;
    private final UnitPrintSettingsService unitPrintSettingsService;
    private final EinsatzAppSettingsService einsatzAppSettingsService;
    private final FcmConfigService fcmConfigService;

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
        populatePrint(model, unitId);
        populateEinsatzapp(model, unitId);
        model.addAttribute("diveraRecipientGroups", diveraMappingService.listRecipientGroups(unitId));
        model.addAttribute("diveraStatusIds", diveraMappingService.listStatusIds(unitId));
    }

    public void populateModule(Model model, long unitId) {
        model.addAttribute("modulesEnabled", moduleSettingsService.modulesEnabled(unitId));
        model.addAttribute("moduleDefs", Arrays.asList(AppModule.values()));
    }

    @Transactional(readOnly = true)
    public void populateTechnik(Model model, long unitId) {
        List<Vehicle> vehicles = unitAdminService.listVehicles(unitId);
        model.addAttribute("vehicles", vehicles);
        model.addAttribute("vehicleTypes", unitVehicleTypeService.list(unitId));
        model.addAttribute("vehicleTypeLabels", unitVehicleTypeService.labelsMap(unitId));
        model.addAttribute("serviceStatusLabels", VehicleServiceStatus.labels());
        model.addAttribute("equipmentCountByVehicleId", unitAdminService.equipmentCountByVehicleId(unitId));
        Long selectedVehicleId = resolveSelectedVehicleId(model.getAttribute("selectedVehicleId"));
        model.addAttribute("selectedVehicleId", selectedVehicleId);
        if (selectedVehicleId != null) {
            Optional<Vehicle> selected = vehicles.stream()
                    .filter(v -> v.getId().equals(selectedVehicleId))
                    .findFirst();
            if (selected.isPresent()) {
                Vehicle v = selected.get();
                model.addAttribute("selectedVehicle", v);
                model.addAttribute("selectedVehicleName", v.getName());
                model.addAttribute("equipmentCategories", unitAdminService.listEquipmentCategories(unitId));
                model.addAttribute(
                        "equipmentRows",
                        unitAdminService.listEquipment(selectedVehicleId).stream()
                                .map(AdminUnitViewService::toEquipmentRow)
                                .collect(Collectors.toList()));
                model.addAttribute("checklistIntervalOptions", ChecklistInterval.options());
                model.addAttribute(
                        "checklistTemplates",
                        vehicleChecklistService.listTemplates(unitId, selectedVehicleId));
                model.addAttribute(
                        "checklistHistory",
                        vehicleChecklistService.listHistory(unitId, selectedVehicleId));
                Long checklistViewId = resolveSelectedVehicleId(model.getAttribute("checklistViewId"));
                if (checklistViewId != null) {
                    vehicleChecklistService
                            .getDetail(unitId, selectedVehicleId, checklistViewId)
                            .ifPresent(d -> model.addAttribute("checklistDetail", d));
                }
            } else {
                model.addAttribute("vehicleNotFound", true);
            }
        } else {
            model.addAttribute("rooms", unitAdminService.listRooms(unitId));
        }
    }

    private static Long resolveSelectedVehicleId(Object param) {
        if (param instanceof Long id) {
            return id;
        }
        if (param instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    @Transactional(readOnly = true)
    public void populateAusbildung(Model model, long unitId) {
        unitRoleService.ensureSystemRoles(unitId);
        model.addAttribute("unitDienstgrade", unitRoleService.listDienstgrade(unitId));
        model.addAttribute("qualificationTypes", personalService.listQualificationTypes(unitId, false));
        model.addAttribute("courses", personalService.listCourses(unitId, false));
    }

    private static EquipmentRow toEquipmentRow(VehicleEquipment eq) {
        var cat = eq.getCategory();
        return new EquipmentRow(
                eq.getId(),
                eq.getName(),
                cat != null ? cat.getId() : null,
                cat != null ? cat.getName() : null);
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

    private void populatePrint(Model model, long unitId) {
        UnitPrintSettings settings = unitPrintSettingsService.requireSettings(unitId);
        model.addAttribute("printSettings", settings);
        model.addAttribute("printModes", PrintMode.values());
        model.addAttribute("cupsClientAvailable", unitPrintSettingsService.isCupsClientAvailable());
        model.addAttribute("defaultCupsServer", unitPrintSettingsService.resolveCupsServer(settings));
    }

    private void populateEinsatzapp(Model model, long unitId) {
        var settings = einsatzAppSettingsService.ensureSettings(unitId);
        model.addAttribute("einsatzappPushEnabled", settings.isPushEnabled());
        model.addAttribute("einsatzappFcmConfigured", fcmConfigService.isConfigured());
        model.addAttribute("einsatzappDeviceCount", einsatzAppSettingsService.countDevices(unitId));
        model.addAttribute("einsatzappModuleEnabled", moduleSettingsService.isEnabled(AppModule.EINSATZAPP, unitId));
    }

    public void populateImportExport(Model model, long unitId) {
        model.addAttribute("importExportUnitId", unitId);
    }
}
