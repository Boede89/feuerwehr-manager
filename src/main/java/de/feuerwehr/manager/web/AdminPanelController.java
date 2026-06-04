package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitDiveraSettings;
import de.feuerwehr.manager.unit.UnitDiveraSettingsRepository;
import de.feuerwehr.manager.unit.UnitService;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'UNIT_ADMIN')")
@RequiredArgsConstructor
public class AdminPanelController {

    private final UnitService unitService;
    private final UnitDiveraSettingsRepository diveraSettingsRepository;
    private final ModuleSettingsService moduleSettingsService;
    private final TestModeService testModeService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "scope", defaultValue = "einheit") String scope,
            @RequestParam(name = "tab", defaultValue = "schnittstellen") String tab,
            Model model) {
        boolean superAdmin = actor.getRole().isSuperAdmin();
        if (!superAdmin) {
            scope = "einheit";
        } else if (!"global".equals(scope)) {
            scope = "einheit";
        }

        if ("global".equals(scope)) {
            tab = normalizeGlobalTab(tab);
        } else {
            tab = normalizeUnitTab(tab);
        }

        model.addAttribute("adminScope", scope);
        model.addAttribute("adminTab", tab);
        model.addAttribute("showGlobalScope", superAdmin);

        if ("global".equals(scope)) {
            model.addAttribute("modulesEnabled", moduleSettingsService.modulesEnabled());
            model.addAttribute("moduleDefs", Arrays.asList(AppModule.values()));
            model.addAttribute("testModeEnabled", testModeService.isEnabled());
            return "admin/index";
        }

        if (unitService.findActiveOrdered(actor).isEmpty()) {
            model.addAttribute("noUnit", true);
            return "admin/index";
        }

        Optional<Unit> unit = unitService.resolveActiveUnit(unitId, actor);
        if (unit.isEmpty()) {
            return "redirect:/admin?scope=einheit&tab=" + tab;
        }

        Unit active = unit.get();
        long resolvedId = active.getId();
        if (unitId == null || !unitId.equals(resolvedId)) {
            return "redirect:/admin?scope=einheit&tab=" + tab + "&unit=" + resolvedId;
        }

        model.addAttribute("unitId", resolvedId);
        model.addAttribute("currentUnitName", active.getName());

        if ("schnittstellen".equals(tab)) {
            populateDivera(model, resolvedId);
        }

        return "admin/index";
    }

    @PostMapping("/modules")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String saveModules(
            @RequestParam Map<String, String> params,
            RedirectAttributes redirectAttributes) {
        Map<String, Boolean> updates = new LinkedHashMap<>();
        for (AppModule module : AppModule.values()) {
            if (!module.implemented()) {
                continue;
            }
            updates.put(module.key(), params.containsKey("module_" + module.key()));
        }
        moduleSettingsService.saveModules(updates);
        redirectAttributes.addFlashAttribute("saved", true);
        redirectAttributes.addFlashAttribute("message", "Module gespeichert.");
        return "redirect:/admin?scope=global&tab=module";
    }

    @PostMapping("/divera")
    public String saveDivera(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestParam String apiBaseUrl,
            @RequestParam(required = false) String accessKey,
            RedirectAttributes redirectAttributes) {

        long resolvedUnitId = unitService
                .resolveActiveUnit(unitId, actor)
                .map(Unit::getId)
                .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit ausgewählt."));

        UnitDiveraSettings settings = diveraSettingsRepository
                .findByUnitId(resolvedUnitId)
                .orElseThrow(() -> new IllegalArgumentException("Keine Divera-Einstellungen für diese Einheit."));

        String base = apiBaseUrl == null ? "" : apiBaseUrl.trim();
        if (base.isEmpty()) {
            base = "https://app.divera247.com";
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        settings.setApiBaseUrl(base);

        if (accessKey != null && !accessKey.isBlank()) {
            settings.setAccessKey(accessKey.trim().replaceAll("[\\r\\n\\t\\v]+", ""));
        }

        diveraSettingsRepository.save(settings);
        redirectAttributes.addFlashAttribute("saved", true);
        redirectAttributes.addFlashAttribute("message", "Divera-Einstellungen gespeichert.");
        return "redirect:/admin?scope=einheit&tab=schnittstellen&unit=" + resolvedUnitId;
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

    private static String normalizeGlobalTab(String tab) {
        return switch (tab) {
            case "module", "benutzer", "einheiten", "testmodus" -> tab;
            default -> "module";
        };
    }

    private static String normalizeUnitTab(String tab) {
        return switch (tab) {
            case "schnittstellen", "personal", "benutzer" -> tab;
            default -> "schnittstellen";
        };
    }
}
