package de.feuerwehr.manager.web;

import de.feuerwehr.manager.berichte.BerichteTab;
import de.feuerwehr.manager.berichte.EinsatzberichtForm;
import de.feuerwehr.manager.berichte.EinsatzberichtService;
import de.feuerwehr.manager.berichte.IncidentReport;
import de.feuerwehr.manager.berichte.IncidentResourceField;
import de.feuerwehr.manager.berichte.IncidentType;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/berichte")
@RequiredArgsConstructor
public class BerichteController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final UserPermissionService userPermissionService;
    private final EinsatzberichtService einsatzberichtService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "tab", defaultValue = "einsatz") String tab,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireBerichteRead(actor, unit.getId());
            BerichteTab berichteTab = BerichteTab.fromKey(tab);
            model.addAttribute("berichteTab", berichteTab.key());
            model.addAttribute("berichteTabs", BerichteTab.values());
            model.addAttribute("canWrite", canWrite(actor, unit.getId()));
            if (berichteTab == BerichteTab.EINSATZ) {
                model.addAttribute("einsatzberichte", einsatzberichtService.listByUnit(unit.getId()));
            }
            return "berichte/index";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectHome(unitId);
        }
    }

    @GetMapping("/einsatzberichte/neu")
    public String newEinsatzbericht(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireBerichteWrite(actor, unit.getId());
            populateEinsatzFormModel(model, unit.getId(), null, einsatzberichtService.newForm(unit.getId()), Map.of());
            model.addAttribute("formMode", "create");
            model.addAttribute("pageTitle", "Neuer Einsatzbericht");
            model.addAttribute("pageSubtitle", "Entwurf — wird nach dem Speichern zur Freigabe vorgelegt");
            return "berichte/einsatzbericht-form";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, "einsatz");
        }
    }

    @GetMapping("/einsatzberichte/{id}/bearbeiten")
    public String editEinsatzbericht(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @PathVariable long id,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireBerichteWrite(actor, unit.getId());
            IncidentReport report = einsatzberichtService.requireReport(unit.getId(), id);
            Map<String, Object> resources = einsatzberichtService.parseResources(report);
            Map<String, String> resourceStrings = resources.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue()), (a, b) -> a, HashMap::new));
            EinsatzberichtForm form = EinsatzberichtForm.fromReport(report, resources);
            populateEinsatzFormModel(model, unit.getId(), report, form, resourceStrings);
            model.addAttribute("formMode", "edit");
            model.addAttribute("pageTitle", "Einsatzbericht bearbeiten");
            model.addAttribute("pageSubtitle", report.getIncidentNumber() != null ? report.getIncidentNumber() : "Entwurf");
            return "berichte/einsatzbericht-form";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, "einsatz");
        }
    }

    @PostMapping("/einsatzberichte")
    public String createEinsatzbericht(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @ModelAttribute EinsatzberichtForm form,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor);
            requireModuleEnabled(unit.getId());
            requireBerichteWrite(actor, unit.getId());
            resolveIncidentTypeLabel(form);
            Map<String, String> flatParams = flattenParams(request);
            einsatzberichtService.create(unit.getId(), form.toData(einsatzberichtService.resourcesFromParams(flatParams)), actor);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Einsatzbericht wurde gespeichert.");
            return redirectBerichte(unit.getId(), "einsatz");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, "einsatz");
        }
    }

    @PostMapping("/einsatzberichte/{id}")
    public String updateEinsatzbericht(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @PathVariable long id,
            @ModelAttribute EinsatzberichtForm form,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor);
            requireModuleEnabled(unit.getId());
            requireBerichteWrite(actor, unit.getId());
            resolveIncidentTypeLabel(form);
            Map<String, String> flatParams = flattenParams(request);
            einsatzberichtService.update(unit.getId(), id, form.toData(einsatzberichtService.resourcesFromParams(flatParams)), actor);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Einsatzbericht wurde aktualisiert.");
            return redirectBerichte(unit.getId(), "einsatz");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, "einsatz");
        }
    }

    @PostMapping("/platzhalter")
    public String placeholderCreate(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "tab") String tab,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor);
            requireModuleEnabled(unit.getId());
            requireBerichteWrite(actor, unit.getId());
            redirectAttributes.addFlashAttribute("error", "Dieser Berichtstyp wird als Nächstes umgesetzt.");
            return redirectBerichte(unit.getId(), tab);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, tab);
        }
    }

    private void populateEinsatzFormModel(
            Model model,
            long unitId,
            IncidentReport report,
            EinsatzberichtForm form,
            Map<String, String> resourceValues) {
        List<IncidentType> types = einsatzberichtService.listActiveIncidentTypes();
        model.addAttribute("report", report);
        model.addAttribute("form", form);
        model.addAttribute("incidentTypes", types);
        model.addAttribute("incidentTypeCategories", types.stream()
                .map(IncidentType::getCategory)
                .distinct()
                .toList());
        model.addAttribute("resourceFields", IncidentResourceField.ALL);
        model.addAttribute("resourceValues", resourceValues);
    }

    private void resolveIncidentTypeLabel(EinsatzberichtForm form) {
        if (form.getIncidentTypeLabel() != null && !form.getIncidentTypeLabel().isBlank()) {
            return;
        }
        einsatzberichtService.listActiveIncidentTypes().stream()
                .filter(t -> t.getTypeKey().equals(form.getIncidentTypeKey()))
                .findFirst()
                .ifPresentOrElse(
                        t -> form.setIncidentTypeLabel(t.getLabel()),
                        () -> form.setIncidentTypeLabel("Sonstiges"));
    }

    private static Map<String, String> flattenParams(HttpServletRequest request) {
        Map<String, String> flat = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                flat.put(key, values[0]);
            }
        });
        return flat;
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

    private Unit resolveUnit(Long unitId, AppUserDetails actor) {
        Unit unit = unitService
                .resolveActiveUnit(unitId, actor)
                .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
        accessControlService.requireUnitAccess(actor, unit.getId());
        return unit;
    }

    private void requireModuleEnabled(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.BERICHTE, unitId)) {
            throw new IllegalArgumentException("Das Modul Berichte ist für diese Einheit nicht aktiviert.");
        }
    }

    private void requireBerichteRead(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "berichte.read");
    }

    private void requireBerichteWrite(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "berichte.write");
    }

    private boolean canWrite(AppUserDetails actor, long unitId) {
        return userPermissionService.hasPermission(actor, unitId, "berichte.write");
    }

    private static String redirectHome(Long unitId) {
        return unitId != null ? "redirect:/?unit=" + unitId : "redirect:/";
    }

    private static String redirectBerichte(Long unitId, String tab) {
        if (unitId != null) {
            return "redirect:/berichte?unit=" + unitId + "&tab=" + tab;
        }
        return "redirect:/berichte?tab=" + tab;
    }
}
