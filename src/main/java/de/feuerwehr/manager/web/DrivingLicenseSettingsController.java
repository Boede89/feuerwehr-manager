package de.feuerwehr.manager.web;

import de.feuerwehr.manager.drivinglicense.DrivingLicenseSettingsService;
import de.feuerwehr.manager.drivinglicense.UnitDrivingLicenseSettings;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.user.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/fuehrerschein")
@RequiredArgsConstructor
public class DrivingLicenseSettingsController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final DrivingLicenseSettingsService drivingLicenseSettingsService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            Unit unit = unitService
                    .resolveActiveUnit(unitId, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
            accessControlService.requireUnitAccess(actor, unit.getId());
            requireModuleEnabled(unit.getId());
            UnitDrivingLicenseSettings settings = drivingLicenseSettingsService.ensureSettings(unit.getId());
            List<User> unitUsers = drivingLicenseSettingsService.listSelectableUnitUsers(unit.getId());
            model.addAttribute("unitId", unit.getId());
            model.addAttribute("currentUnitName", unit.getName());
            model.addAttribute("settings", settings);
            model.addAttribute("unitUsers", unitUsers);
            model.addAttribute(
                    "notificationUserIds", drivingLicenseSettingsService.parseNotificationUserIds(settings));
            return "settings/fuehrerschein";
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId != null ? "redirect:/admin?scope=einheit&tab=module&unit=" + unitId : "redirect:/settings";
        }
    }

    @PostMapping
    public String save(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam int intervalMonths,
            @RequestParam int warnDays,
            @RequestParam(required = false, defaultValue = "false") boolean notifyPerson,
            @RequestParam(name = "notificationUserIds", required = false) Long[] notificationUserIds,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            drivingLicenseSettingsService.save(unit, intervalMonths, warnDays, notifyPerson, notificationUserIds);
            redirectAttributes.addFlashAttribute("message", "Führerschein-Einstellungen gespeichert.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings/fuehrerschein?unit=" + unit;
    }

    private void requireModuleEnabled(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.PERSONAL, unitId)) {
            throw new IllegalStateException("Das Modul Personal ist für diese Einheit nicht aktiviert.");
        }
    }
}
