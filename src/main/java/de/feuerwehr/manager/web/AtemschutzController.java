package de.feuerwehr.manager.web;

import de.feuerwehr.manager.atemschutz.AtemschutzCarrier;
import de.feuerwehr.manager.atemschutz.AtemschutzCarrierStatus;
import de.feuerwehr.manager.atemschutz.AtemschutzFitnessType;
import de.feuerwehr.manager.atemschutz.AtemschutzPlanStatus;
import de.feuerwehr.manager.atemschutz.AtemschutzService;
import de.feuerwehr.manager.atemschutz.AtemschutzService.CarrierDetailView;
import de.feuerwehr.manager.atemschutz.AtemschutzService.CarrierListResult;
import de.feuerwehr.manager.atemschutz.AtemschutzService.CarrierOverview;
import de.feuerwehr.manager.atemschutz.AtemschutzService.UebungPlanResult;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

    private static final DateTimeFormatter PRINT_STAMP_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY);

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
            CarrierListResult result = atemschutzService.listCarrierOverviews(unit.getId(), filter);
            populateListModel(model, result, filter);
            model.addAttribute("canWrite", canWrite(actor, unit.getId()));
            model.addAttribute("warnDays", atemschutzService.warnDays(unit.getId()));
            return "atemschutz/index";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectHome(unitId);
        }
    }

    @GetMapping("/drucken")
    public String printList(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "filter", defaultValue = "all") String filter,
            @RequestParam(name = "paused", defaultValue = "false") boolean includePaused,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireAtemschutzRead(actor, unit.getId());
            CarrierListResult result = atemschutzService.listCarrierOverviews(unit.getId(), filter);
            List<CarrierOverview> carriers = result.carriers();
            if (!includePaused) {
                carriers = carriers.stream()
                        .filter(row -> row.carrier().getStatus() == AtemschutzCarrierStatus.ACTIVE)
                        .toList();
            }
            String normalizedFilter = normalizeFilter(filter);
            String subtitle = unit.getName()
                    + " · Filter: "
                    + filterLabel(normalizedFilter)
                    + (includePaused ? " · inkl. Pausierte" : "")
                    + " · Stand: "
                    + printTimestamp();
            addPrintPageHeader(model, unit, "Atemschutz – Geräteträgerliste", subtitle);
            model.addAttribute("carriers", carriers);
            model.addAttribute("activeFilter", normalizedFilter);
            model.addAttribute("includePaused", includePaused);
            model.addAttribute("filterLabel", filterLabel(normalizedFilter));
            return "atemschutz/liste-druck";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId != null ? "redirect:/atemschutz?unit=" + unitId : redirectHome(unitId);
        }
    }

    @GetMapping("/uebung-planen")
    public String planUebungForm(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireAtemschutzRead(actor, unit.getId());
            populateUebungPlanForm(model, LocalDate.now(), "alle", defaultPlanStatusSelection());
            return "atemschutz/uebung-planen";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectHome(unitId);
        }
    }

    @PostMapping("/uebung-planen")
    public String planUebungSearch(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate uebungsDatum,
            @RequestParam(name = "anzahlPaTraeger", defaultValue = "alle") String anzahlPaTraeger,
            @RequestParam(name = "statusFilter", required = false) String[] statusFilter,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit resolved = resolveUnit(unit, actor, model);
            requireModuleEnabled(resolved.getId());
            requireAtemschutzRead(actor, resolved.getId());

            Set<AtemschutzPlanStatus> selectedStatuses = parsePlanStatuses(statusFilter);
            if (selectedStatuses.isEmpty()) {
                throw new IllegalArgumentException("Bitte mindestens einen Status auswählen.");
            }

            int limit = parseCarrierLimit(anzahlPaTraeger);
            UebungPlanResult result =
                    atemschutzService.planUebung(resolved.getId(), uebungsDatum, selectedStatuses, limit);

            model.addAttribute("planResult", result);
            model.addAttribute("anzahlLabel", formatAnzahlLabel(anzahlPaTraeger));
            model.addAttribute("anzahlPaTraeger", anzahlPaTraeger);
            model.addAttribute("uebungsDatum", uebungsDatum);
            model.addAttribute("selectedStatusParams", statusFilter != null ? statusFilter : new String[0]);
            return "atemschutz/uebung-planen-ergebnisse";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/atemschutz/uebung-planen?unit=" + unit;
        }
    }

    @GetMapping("/uebung-planen/drucken")
    public String printUebungPlan(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate uebungsDatum,
            @RequestParam(name = "anzahlPaTraeger", defaultValue = "alle") String anzahlPaTraeger,
            @RequestParam(name = "statusFilter", required = false) String[] statusFilter,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit resolved = resolveUnit(unit, actor, model);
            requireModuleEnabled(resolved.getId());
            requireAtemschutzRead(actor, resolved.getId());

            Set<AtemschutzPlanStatus> selectedStatuses = parsePlanStatuses(statusFilter);
            if (selectedStatuses.isEmpty()) {
                throw new IllegalArgumentException("Bitte mindestens einen Status auswählen.");
            }

            int limit = parseCarrierLimit(anzahlPaTraeger);
            UebungPlanResult result =
                    atemschutzService.planUebung(resolved.getId(), uebungsDatum, selectedStatuses, limit);

            addPrintPageHeader(
                    model,
                    resolved,
                    "PA-Träger Suchergebnisse",
                    resolved.getName() + " · Stand: " + printTimestamp());
            model.addAttribute("planResult", result);
            model.addAttribute("anzahlLabel", formatAnzahlLabel(anzahlPaTraeger));
            return "atemschutz/uebung-planen-druck";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/atemschutz/uebung-planen?unit=" + unit;
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
            CarrierDetailView detail = atemschutzService.loadCarrierDetail(id);
            model.addAttribute("carrier", carrier);
            model.addAttribute("person", person);
            model.addAttribute("detail", detail);
            model.addAttribute("canWrite", canWrite(actor, unit.getId()));
            model.addAttribute("fitnessTypes", AtemschutzFitnessType.values());
            model.addAttribute("carrierStatuses", AtemschutzCarrierStatus.values());
            model.addAttribute("warnDays", atemschutzService.warnDays(unit.getId()));
            return "atemschutz/carrier-detail";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId != null ? "redirect:/atemschutz?unit=" + unitId : redirectHome(unitId);
        }
    }

    @PostMapping("/bulk-records")
    public String bulkAddRecords(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "carrierIds") Long[] carrierIds,
            @RequestParam AtemschutzFitnessType recordType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
            RedirectAttributes redirectAttributes) {
        try {
            requireModuleEnabled(unit);
            requireAtemschutzWrite(actor, unit);
            accessControlService.requireUnitAccess(actor, unit);
            int count = atemschutzService.bulkAddFitnessRecords(
                    unit, carrierIds != null ? Arrays.asList(carrierIds) : List.of(), recordType, validFrom, actor.getUserId());
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", count + " Nachweis(e) gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/atemschutz?unit=" + unit;
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

    private void populateListModel(Model model, CarrierListResult result, String filter) {
        model.addAttribute("carriers", result.carriers());
        model.addAttribute("carrierCount", result.carriers().size());
        model.addAttribute("stats", result.stats());
        model.addAttribute("statsAll", result.statsAll());
        model.addAttribute("activeFilter", normalizeFilter(filter));
        model.addAttribute("agtCourseName", result.agtCourseName());
        model.addAttribute("agtCourseConfigured", result.agtCourseConfigured());
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

    private static void addPrintPageHeader(Model model, Unit unit, String title, String subtitle) {
        if (unit.getLogoBase64() != null && !unit.getLogoBase64().isBlank()) {
            model.addAttribute("unitLogoBase64", unit.getLogoBase64());
        }
        model.addAttribute("printTitle", title);
        model.addAttribute("printSubtitle", subtitle);
    }

    private static String printTimestamp() {
        return PRINT_STAMP_FMT.format(LocalDateTime.now()) + " Uhr";
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

    private static void populateUebungPlanForm(
            Model model, LocalDate uebungsDatum, String anzahlPaTraeger, Set<AtemschutzPlanStatus> selectedStatuses) {
        model.addAttribute("uebungsDatum", uebungsDatum);
        model.addAttribute("anzahlPaTraeger", anzahlPaTraeger);
        model.addAttribute("planStatuses", AtemschutzPlanStatus.values());
        model.addAttribute("selectedPlanStatuses", selectedStatuses);
    }

    private static Set<AtemschutzPlanStatus> defaultPlanStatusSelection() {
        return AtemschutzPlanStatus.DEFAULT_SELECTED.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AtemschutzPlanStatus.class)));
    }

    private static Set<AtemschutzPlanStatus> parsePlanStatuses(String[] statusFilter) {
        if (statusFilter == null || statusFilter.length == 0) {
            return Set.of();
        }
        return Arrays.stream(statusFilter)
                .map(AtemschutzPlanStatus::fromParam)
                .filter(status -> status != null)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AtemschutzPlanStatus.class)));
    }

    private static int parseCarrierLimit(String anzahlPaTraeger) {
        if (anzahlPaTraeger == null || anzahlPaTraeger.isBlank() || "alle".equalsIgnoreCase(anzahlPaTraeger)) {
            return 0;
        }
        try {
            int limit = Integer.parseInt(anzahlPaTraeger.trim());
            if (limit < 1 || limit > 50) {
                throw new IllegalArgumentException("Ungültige Anzahl PA-Träger.");
            }
            return limit;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ungültige Anzahl PA-Träger.");
        }
    }

    private static String formatAnzahlLabel(String anzahlPaTraeger) {
        if (anzahlPaTraeger == null || anzahlPaTraeger.isBlank() || "alle".equalsIgnoreCase(anzahlPaTraeger)) {
            return "Alle verfügbaren";
        }
        return anzahlPaTraeger + " PA-Träger";
    }

    private static String normalizeFilter(String filter) {
        if ("tauglich".equalsIgnoreCase(filter)) {
            return "tauglich";
        }
        if ("warnung".equalsIgnoreCase(filter)) {
            return "warnung";
        }
        if ("uebung_abgelaufen".equalsIgnoreCase(filter) || "uebungabgelaufen".equalsIgnoreCase(filter)) {
            return "uebung_abgelaufen";
        }
        if ("nicht_tauglich".equalsIgnoreCase(filter) || "nichttauglich".equalsIgnoreCase(filter)) {
            return "nicht_tauglich";
        }
        return "all";
    }

    private static String filterLabel(String filter) {
        return switch (filter) {
            case "tauglich" -> "Tauglich";
            case "warnung" -> "Warnung";
            case "uebung_abgelaufen" -> "Übung abgelaufen";
            case "nicht_tauglich" -> "Nicht tauglich";
            default -> "Alle";
        };
    }
}
