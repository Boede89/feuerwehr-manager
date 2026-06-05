package de.feuerwehr.manager.web;

import de.feuerwehr.manager.atemschutz.AtemschutzCarrier;
import de.feuerwehr.manager.atemschutz.AtemschutzCarrierStatus;
import de.feuerwehr.manager.atemschutz.AtemschutzFitnessType;
import de.feuerwehr.manager.atemschutz.AtemschutzService;
import de.feuerwehr.manager.atemschutz.AtemschutzService.CarrierDetailView;
import de.feuerwehr.manager.atemschutz.AtemschutzService.CarrierListResult;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/atemschutz")
@RequiredArgsConstructor
public class AtemschutzController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final UserPermissionService userPermissionService;
    private final AtemschutzService atemschutzService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "filter", defaultValue = "all") String filter,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireAtemschutzRead(actor, unit.getId());
            boolean includeHealth = actor.getRole().isAdminLevel();
            CarrierListResult result =
                    atemschutzService.listCarrierOverviews(unit.getId(), includeHealth, filter);
            model.addAttribute("carriers", result.carriers());
            model.addAttribute("carrierCount", result.carriers().size());
            model.addAttribute("stats", result.stats());
            model.addAttribute("activeFilter", normalizeFilter(filter));
            model.addAttribute("agtCourseName", result.agtCourseName());
            model.addAttribute("agtCourseConfigured", result.agtCourseConfigured());
            model.addAttribute("canWrite", canWrite(actor, unit.getId()));
            model.addAttribute("warnDays", atemschutzService.warnDays(unit.getId()));
            return "atemschutz/index";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectHome(unitId);
        }
    }

    @GetMapping("/carriers/{id}")
    public String detail(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
        Unit unit = resolveUnit(unitId, actor, model);
        requireModuleEnabled(unit.getId());
        requireAtemschutzRead(actor, unit.getId());
        AtemschutzCarrier carrier = atemschutzService.requireCarrier(id);
        accessControlService.requireUnitAccess(actor, carrier.getUnit().getId());
        Person person = carrier.getPerson();
        boolean includeHealth = atemschutzService.canViewHealthData(actor, person);
        CarrierDetailView detail = atemschutzService.loadCarrierDetail(id, includeHealth);
        model.addAttribute("carrier", carrier);
        model.addAttribute("person", person);
        model.addAttribute("detail", detail);
        model.addAttribute("canWrite", canWrite(actor, unit.getId()));
        model.addAttribute("canViewHealthData", includeHealth);
        model.addAttribute("fitnessTypes", AtemschutzFitnessType.values());
        model.addAttribute("carrierStatuses", AtemschutzCarrierStatus.values());
            model.addAttribute("warnDays", atemschutzService.warnDays(unit.getId()));
        return "atemschutz/carrier-detail";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId != null ? "redirect:/atemschutz?unit=" + unitId : redirectHome(unitId);
        }
    }

    @PostMapping("/carriers/{id}")
    public String updateCarrier(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam long unit,
            @RequestParam(required = false) AtemschutzCarrierStatus status,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {
        try {
            requireModuleEnabled(unit);
            requireAtemschutzWrite(actor, unit);
            AtemschutzCarrier carrier = atemschutzService.requireCarrier(id);
            accessControlService.requireUnitAccess(actor, carrier.getUnit().getId());
            atemschutzService.updateCarrier(id, status, notes);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Geräteträger gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/atemschutz/carriers/" + id + "?unit=" + unit;
    }

    @PostMapping("/carriers/{id}/delete")
    public String deleteCarrier(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam long unit,
            RedirectAttributes redirectAttributes) {
        try {
            requireModuleEnabled(unit);
            requireAtemschutzWrite(actor, unit);
            accessControlService.requireAdminLevel(actor);
            AtemschutzCarrier carrier = atemschutzService.requireCarrier(id);
            accessControlService.requireUnitAccess(actor, carrier.getUnit().getId());
            atemschutzService.removeCarrier(id);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Geräteträger wurde entfernt.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/atemschutz?unit=" + unit;
    }

    @PostMapping("/carriers/{id}/records")
    public String addRecord(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam long unit,
            @RequestParam AtemschutzFitnessType recordType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
            RedirectAttributes redirectAttributes) {
        try {
            requireModuleEnabled(unit);
            requireAtemschutzWrite(actor, unit);
            AtemschutzCarrier carrier = atemschutzService.requireCarrier(id);
            accessControlService.requireUnitAccess(actor, carrier.getUnit().getId());
            atemschutzService.addFitnessRecord(id, recordType, validFrom, actor.getUserId());
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Nachweis gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/atemschutz/carriers/" + id + "?unit=" + unit;
    }

    @PostMapping("/carriers/{id}/records/{rid}/delete")
    public String deleteRecord(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @PathVariable long rid,
            @RequestParam long unit,
            RedirectAttributes redirectAttributes) {
        try {
            requireModuleEnabled(unit);
            requireAtemschutzWrite(actor, unit);
            AtemschutzCarrier carrier = atemschutzService.requireCarrier(id);
            accessControlService.requireUnitAccess(actor, carrier.getUnit().getId());
            atemschutzService.deleteFitnessRecord(rid);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Nachweis entfernt.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/atemschutz/carriers/" + id + "?unit=" + unit;
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
        if (!moduleSettingsService.isEnabled(AppModule.ATEMSCHUTZ, unitId)) {
            throw new IllegalArgumentException("Das Modul Atemschutz ist für diese Einheit nicht aktiviert.");
        }
    }

    private void requireAtemschutzRead(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "atemschutz.read");
    }

    private void requireAtemschutzWrite(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "atemschutz.write");
    }

    private boolean canWrite(AppUserDetails actor, long unitId) {
        return userPermissionService.hasPermission(actor, unitId, "atemschutz.write");
    }

    private static String redirectHome(Long unitId) {
        return unitId != null ? "redirect:/?unit=" + unitId : "redirect:/";
    }

    private static String normalizeFilter(String filter) {
        if ("tauglich".equalsIgnoreCase(filter)) {
            return "tauglich";
        }
        if ("nicht_tauglich".equalsIgnoreCase(filter) || "nichttauglich".equalsIgnoreCase(filter)) {
            return "nicht_tauglich";
        }
        return "all";
    }
}
