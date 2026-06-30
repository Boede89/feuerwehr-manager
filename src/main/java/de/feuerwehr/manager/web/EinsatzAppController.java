package de.feuerwehr.manager.web;

import de.feuerwehr.manager.einsatzapp.EinsatzAppSettingsService;
import de.feuerwehr.manager.einsatzapp.EinsatzappPushLog;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/einsatzapp")
@RequiredArgsConstructor
public class EinsatzAppController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final UserPermissionService userPermissionService;
    private final EinsatzAppSettingsService einsatzAppSettingsService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireEinsatzAppRead(actor, unit.getId());
            model.addAttribute("pageTitle", "Einsatz-App");
            model.addAttribute("pageSubtitle", "Push-Alarmierung bei DIVERA-Einsätzen");
            model.addAttribute("pushEnabled", einsatzAppSettingsService.isPushEnabled(unit.getId()));
            model.addAttribute("fcmConfigured", einsatzAppSettingsService.isFcmConfigured());
            model.addAttribute("deviceCount", einsatzAppSettingsService.countDevices(unit.getId()));
            List<EinsatzappPushLog> recentPushLog = einsatzAppSettingsService.recentPushLog(unit.getId());
            model.addAttribute("recentPushLog", recentPushLog);
            return "einsatzapp/index";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectHome(unitId);
        }
    }

    private Unit resolveUnit(Long unitId, AppUserDetails actor, Model model) {
        Unit unit = unitService
                .resolveActiveUnit(unitId, actor)
                .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
        accessControlService.requireUnitAccess(actor, unit.getId());
        model.addAttribute("unitId", unit.getId());
        model.addAttribute("currentUnitName", unit.getName());
        return unit;
    }

    private void requireModuleEnabled(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.EINSATZAPP, unitId)) {
            throw new IllegalArgumentException("Das Modul Einsatz-App ist für diese Einheit nicht aktiviert.");
        }
    }

    private void requireEinsatzAppRead(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "einsatzapp.read");
    }

    private static String redirectHome(Long unitId) {
        return unitId != null ? "redirect:/?unit=" + unitId : "redirect:/";
    }
}
