package de.feuerwehr.manager.web;

import de.feuerwehr.manager.pdf.HtmlPdfService;
import de.feuerwehr.manager.pdf.PdfDownloadResponse;
import de.feuerwehr.manager.personal.CourseDetailPlanService;
import de.feuerwehr.manager.personal.CourseDetailPlanService.CourseSeatInput;
import de.feuerwehr.manager.personal.CourseDetailPlanService.DetailPlanView;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/personal/lehrgangsplanung/detail")
@RequiredArgsConstructor
public class CourseDetailPlanController {

    private static final DateTimeFormatter PRINT_STAMP_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY);

    private final UnitService unitService;
    private final AccessControlService accessControlService;
    private final UserPermissionService userPermissionService;
    private final CourseDetailPlanService courseDetailPlanService;
    private final HtmlPdfService htmlPdfService;

    @GetMapping
    public String view(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "jahr", required = false) Integer jahr,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            int planYear = resolveYear(unit.getId(), jahr);
            DetailPlanView view = courseDetailPlanService.loadView(unit.getId(), planYear);
            List<Integer> yearOptions = new ArrayList<>(courseDetailPlanService.yearOptions(unit.getId()));
            if (!yearOptions.contains(planYear)) {
                yearOptions.add(0, planYear);
            }
            model.addAttribute("planView", view);
            model.addAttribute("planYear", planYear);
            model.addAttribute("yearOptions", yearOptions);
            model.addAttribute("canWrite", canWrite(actor, unit.getId()));
            return "personal/lehrgangsplanung-detail";
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId != null
                    ? "redirect:/personal?unit=" + unitId + "&tab=lehrgangsplanung"
                    : "redirect:/personal";
        }
    }

    @GetMapping("/pdf")
    public Object pdf(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestParam(name = "jahr", required = false) Integer jahr,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            int planYear = resolveYear(unit.getId(), jahr);
            DetailPlanView view = courseDetailPlanService.loadView(unit.getId(), planYear);
            if (view.plan() == null) {
                redirectAttributes.addFlashAttribute("error", "Für " + planYear + " liegt noch keine gespeicherte Planung vor.");
                return redirect(unit.getId(), planYear);
            }
            if (unit.getLogoBase64() != null && !unit.getLogoBase64().isBlank()) {
                model.addAttribute("unitLogoBase64", unit.getLogoBase64());
            }
            String subtitle = unit.getName()
                    + (view.plan().isUseParticipation()
                            ? " · sortiert nach Dienstbeteiligung " + view.participationYear()
                            : "")
                    + " · Stand: "
                    + PRINT_STAMP_FMT.format(LocalDateTime.now())
                    + " Uhr";
            model.addAttribute("printTitle", "Lehrgangsplanung " + planYear);
            model.addAttribute("printSubtitle", subtitle);
            model.addAttribute("planView", view);
            model.addAttribute("planYear", planYear);
            byte[] pdf = htmlPdfService.renderPdf("personal/lehrgangsplanung-detail-druck", model);
            return PdfDownloadResponse.inline("Lehrgangsplanung-Detail-" + planYear + ".pdf", pdf);
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId > 0
                    ? redirect(unitId, jahr != null ? jahr : CourseDetailPlanService.defaultPlanYear())
                    : "redirect:/personal";
        }
    }

    @PostMapping
    public String save(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam int jahr,
            @RequestParam(name = "useParticipation", defaultValue = "false") boolean useParticipation,
            @RequestParam(name = "resort", defaultValue = "false") boolean resort,
            @RequestParam(name = "includeCourseIds", required = false) List<Long> includeCourseIds,
            @RequestParam Map<String, String> params,
            RedirectAttributes redirectAttributes) {
        try {
            requireWrite(actor, unit);
            List<CourseSeatInput> seats = parseSeats(includeCourseIds, params);
            courseDetailPlanService.saveAndGenerate(unit, jahr, useParticipation, seats, resort);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Lehrgangsplanung für " + jahr + " gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirect(unit, jahr);
    }

    @PostMapping("/entries/{entryId}/move")
    public String move(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long entryId,
            @RequestParam long unit,
            @RequestParam int jahr,
            @RequestParam(name = "itemId", required = false) Long itemId,
            @RequestParam String direction,
            RedirectAttributes redirectAttributes) {
        try {
            requireWrite(actor, unit);
            courseDetailPlanService.moveEntry(unit, entryId, direction);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Reihenfolge gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        String url = redirect(unit, jahr);
        if (itemId != null && itemId > 0) {
            url += "#item-" + itemId;
        }
        return url;
    }

    @PostMapping("/entries/{entryId}/confirm")
    public String confirm(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long entryId,
            @RequestParam long unit,
            @RequestParam int jahr,
            @RequestParam(name = "itemId", required = false) Long itemId,
            @RequestParam(name = "confirmed", defaultValue = "false") boolean confirmed,
            RedirectAttributes redirectAttributes) {
        try {
            requireWrite(actor, unit);
            courseDetailPlanService.setConfirmed(unit, entryId, confirmed);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute(
                    "message", confirmed ? "Teilnahme bestätigt." : "Bestätigung entfernt.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        String url = redirect(unit, jahr);
        if (itemId != null && itemId > 0) {
            url += "#item-" + itemId;
        }
        return url;
    }

    @PostMapping("/items/{itemId}/reorder")
    public String reorder(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long itemId,
            @RequestParam long unit,
            @RequestParam int jahr,
            @RequestParam(name = "entryIds", required = false) List<Long> entryIds,
            RedirectAttributes redirectAttributes) {
        try {
            requireWrite(actor, unit);
            courseDetailPlanService.reorderEntries(unit, itemId, entryIds);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Reihenfolge gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirect(unit, jahr) + "#item-" + itemId;
    }

    @PostMapping("/delete")
    public String delete(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam int jahr,
            RedirectAttributes redirectAttributes) {
        try {
            requireWrite(actor, unit);
            courseDetailPlanService.deletePlan(unit, jahr);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Planung für " + jahr + " gelöscht.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirect(unit, jahr);
    }

    private int resolveYear(long unitId, Integer requested) {
        if (requested != null && requested >= 2000 && requested <= 2100) {
            return requested;
        }
        return CourseDetailPlanService.defaultPlanYear();
    }

    private static List<CourseSeatInput> parseSeats(List<Long> includeCourseIds, Map<String, String> params) {
        List<CourseSeatInput> result = new ArrayList<>();
        if (includeCourseIds == null) {
            return result;
        }
        for (Long courseId : includeCourseIds) {
            if (courseId == null || courseId <= 0) {
                continue;
            }
            int seats = parsePositiveInt(params.get("seats_" + courseId), 0);
            if (seats > 0) {
                result.add(new CourseSeatInput(courseId, seats));
            }
        }
        return result;
    }

    private static int parsePositiveInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String redirect(long unitId, int jahr) {
        return "redirect:/personal/lehrgangsplanung/detail?unit=" + unitId + "&jahr=" + jahr;
    }

    private Unit resolveUnit(Long unitId, AppUserDetails actor, Model model) {
        Optional<Unit> unit = unitService.resolveActiveUnit(unitId, actor);
        if (unit.isEmpty()) {
            throw new IllegalStateException("Keine aktive Einheit");
        }
        Unit resolved = unit.get();
        model.addAttribute("unitId", resolved.getId());
        model.addAttribute("currentUnitName", resolved.getName());
        model.addAttribute("units", unitService.findActiveOrdered(actor));
        model.addAttribute("unitSwitchDisabled", actor != null && !actor.getRole().isSuperAdmin());
        userPermissionService.requirePermission(actor, resolved.getId(), "personal.read");
        return resolved;
    }

    private void requireWrite(AppUserDetails actor, long unitId) {
        accessControlService.requireUnitAccess(actor, unitId);
        userPermissionService.requirePermission(actor, unitId, "personal.write");
    }

    private boolean canWrite(AppUserDetails actor, long unitId) {
        return userPermissionService.hasPermission(actor, unitId, "personal.write");
    }
}
