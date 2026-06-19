package de.feuerwehr.manager.web;

import de.feuerwehr.manager.berichte.BerichteSettingsService;
import de.feuerwehr.manager.berichte.BerichteTab;
import de.feuerwehr.manager.berichte.UnitBerichteSettings;
import de.feuerwehr.manager.divera.UnitDiveraStatusId;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.util.Arrays;
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
@RequestMapping("/settings/berichte")
@RequiredArgsConstructor
public class BerichteSettingsController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final BerichteSettingsService berichteSettingsService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "tab", defaultValue = "einsatz") String tab,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            Unit unit = unitService
                    .resolveActiveUnit(unitId, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
            accessControlService.requireUnitAccess(actor, unit.getId());
            requireModuleEnabled(unit.getId());
            UnitBerichteSettings settings = berichteSettingsService.ensureSettings(unit.getId());
            List<UnitDiveraStatusId> statusOptions = berichteSettingsService.listSelectableStatusIds(unit.getId());
            BerichteTab berichteTab = BerichteTab.fromKey(tab);
            model.addAttribute("unitId", unit.getId());
            model.addAttribute("currentUnitName", unit.getName());
            model.addAttribute("berichteTab", berichteTab.key());
            model.addAttribute("berichteTabs", BerichteTab.values());
            model.addAttribute("settings", settings);
            model.addAttribute("diveraStatusOptions", statusOptions);
            model.addAttribute(
                    "selectedPersonnelStatusIds", berichteSettingsService.parsePersonnelStatusIds(settings));
            return "settings/berichte";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId != null ? "redirect:/admin?scope=einheit&tab=module&unit=" + unitId : "redirect:/settings";
        }
    }

    @PostMapping("/einsatz")
    public String saveEinsatz(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "importIncidentDataFromDivera", defaultValue = "false") boolean importIncidentDataFromDivera,
            @RequestParam(name = "importPersonnelFromDivera", defaultValue = "false") boolean importPersonnelFromDivera,
            @RequestParam(name = "allowForeignUnitPersonnel", defaultValue = "false") boolean allowForeignUnitPersonnel,
            @RequestParam(name = "personnelStatusIds", required = false) String[] personnelStatusIds,
            @RequestParam(name = "einsatzReleaseCreateGeraetewart", defaultValue = "false") boolean einsatzReleaseCreateGeraetewart,
            @RequestParam(name = "einsatzReleasePrintReport", defaultValue = "false") boolean einsatzReleasePrintReport,
            @RequestParam(name = "einsatzReleasePrintGeraetewart", defaultValue = "false") boolean einsatzReleasePrintGeraetewart,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            List<String> statusIds = personnelStatusIds == null ? List.of() : Arrays.asList(personnelStatusIds);
            berichteSettingsService.saveEinsatzSettings(
                    unit,
                    importIncidentDataFromDivera,
                    importPersonnelFromDivera,
                    allowForeignUnitPersonnel,
                    statusIds,
                    einsatzReleaseCreateGeraetewart,
                    einsatzReleasePrintReport,
                    einsatzReleasePrintGeraetewart);
            redirectAttributes.addFlashAttribute("message", "Einsatzbericht-Einstellungen gespeichert.");
            return "redirect:/settings/berichte?unit=" + unit + "&tab=einsatz";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/berichte?unit=" + unit + "&tab=einsatz";
        }
    }

    @PostMapping("/anwesenheit")
    public String saveAnwesenheit(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "anwesenheitReleasePrintReport", defaultValue = "false") boolean anwesenheitReleasePrintReport,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            berichteSettingsService.saveAnwesenheitReleaseSettings(unit, anwesenheitReleasePrintReport);
            redirectAttributes.addFlashAttribute("message", "Anwesenheitslisten-Einstellungen gespeichert.");
            return "redirect:/settings/berichte?unit=" + unit + "&tab=anwesenheit";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/berichte?unit=" + unit + "&tab=anwesenheit";
        }
    }

    private void requireModuleEnabled(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.BERICHTE, unitId)) {
            throw new IllegalArgumentException("Das Modul Berichte ist für diese Einheit nicht aktiviert.");
        }
    }
}
