package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.termine.TermineTab;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/termine")
@RequiredArgsConstructor
public class TermineController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final UserPermissionService userPermissionService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "tab", defaultValue = "meine") String tab,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireTermineRead(actor, unit.getId());
            TermineTab termineTab = TermineTab.fromKey(tab);
            model.addAttribute("termineTab", termineTab.key());
            model.addAttribute("termineTabs", TermineTab.values());
            model.addAttribute("canWrite", canWrite(actor, unit.getId()));
            return "termine/index";
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
        if (!moduleSettingsService.isEnabled(AppModule.TERMINE, unitId)) {
            throw new IllegalArgumentException("Das Modul Termine ist für diese Einheit nicht aktiviert.");
        }
    }

    private void requireTermineRead(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "termine.read");
    }

    private boolean canWrite(AppUserDetails actor, long unitId) {
        return userPermissionService.hasPermission(actor, unitId, "termine.write");
    }

    private static String redirectHome(Long unitId) {
        return unitId != null ? "redirect:/?unit=" + unitId : "redirect:/";
    }
}
