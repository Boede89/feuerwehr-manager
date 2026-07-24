package de.feuerwehr.manager.web;

import de.feuerwehr.manager.auswertung.AuswertungBereich;
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
@RequestMapping("/auswertung")
@RequiredArgsConstructor
public class AuswertungController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final UserPermissionService userPermissionService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "bereich", required = false) String bereichKey,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireRead(actor, unit.getId());

            AuswertungBereich bereich = AuswertungBereich.fromKey(bereichKey);
            model.addAttribute("bereich", bereich);
            model.addAttribute(
                    "bereiche",
                    List.of(
                            AuswertungBereich.UEBERSICHT,
                            AuswertungBereich.PERSONEN,
                            AuswertungBereich.EINSAETZE,
                            AuswertungBereich.FAHRZEUGE,
                            AuswertungBereich.GERAETE));
            return "auswertung/index";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/?unit=" + (unitId != null ? unitId : "");
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
        if (!moduleSettingsService.isEnabled(AppModule.AUSWERTUNG, unitId)) {
            throw new IllegalArgumentException("Das Modul Auswertung ist für diese Einheit nicht aktiviert.");
        }
    }

    private void requireRead(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "auswertung.read");
    }
}
