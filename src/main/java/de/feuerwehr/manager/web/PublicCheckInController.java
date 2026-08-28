package de.feuerwehr.manager.web;

import de.feuerwehr.manager.berichte.AttendanceCheckInPageView;
import de.feuerwehr.manager.berichte.AttendanceCheckInService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/check-in")
@RequiredArgsConstructor
public class PublicCheckInController {

    private final AttendanceCheckInService attendanceCheckInService;
    private final ModuleSettingsService moduleSettingsService;
    private final UnitService unitService;

    @GetMapping
    public String startCheckIn(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestParam(name = "terminId") long terminId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            requireBerichteModule(unitId);
            AttendanceCheckInPageView page = attendanceCheckInService.openCheckIn(unitId, terminId, actor);
            return renderCheckInPage(actor, unitId, page, model);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return actor != null ? "redirect:/?unit=" + unitId : "redirect:/login";
        }
    }

    @GetMapping("/{id}")
    public String resumeCheckIn(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long id,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            requireBerichteModule(unitId);
            AttendanceCheckInPageView page = attendanceCheckInService.loadCheckIn(unitId, id);
            return renderCheckInPage(actor, unitId, page, model);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return actor != null ? "redirect:/?unit=" + unitId : "redirect:/login";
        }
    }

    @PostMapping("/{id}/person/{personId}")
    @ResponseBody
    public Object checkInPerson(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long id,
            @PathVariable long personId) {
        try {
            requireBerichteModule(unitId);
            return attendanceCheckInService.checkInPerson(unitId, id, personId, actor);
        } catch (IllegalArgumentException e) {
            return Map.of("ok", false, "message", e.getMessage());
        }
    }

    @DeleteMapping("/{id}/person/{personId}")
    @ResponseBody
    public Object checkOutPerson(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long id,
            @PathVariable long personId) {
        try {
            requireBerichteModule(unitId);
            return attendanceCheckInService.checkOutPerson(unitId, id, personId, actor);
        } catch (IllegalArgumentException e) {
            return Map.of("ok", false, "message", e.getMessage());
        }
    }

    @PostMapping("/{id}/theme")
    @ResponseBody
    public Object updateCheckInTheme(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long id,
            @RequestBody Map<String, String> body) {
        try {
            requireBerichteModule(unitId);
            String theme = body != null ? body.get("theme") : null;
            return attendanceCheckInService.updateTheme(unitId, id, theme, actor);
        } catch (IllegalArgumentException e) {
            return Map.of("ok", false, "message", e.getMessage());
        }
    }

    @PostMapping("/{id}/finish")
    public String finishCheckIn(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long id,
            RedirectAttributes redirectAttributes) {
        try {
            requireBerichteModule(unitId);
            long reportId = attendanceCheckInService.finishCheckIn(unitId, id, actor);
            return "redirect:/berichte/anwesenheitslisten/" + reportId + "/bearbeiten?unit=" + unitId;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/check-in/" + id + "?unit=" + unitId;
        }
    }

    private String renderCheckInPage(
            AppUserDetails actor, long unitId, AttendanceCheckInPageView page, Model model) {
        unitService.findActiveOrdered().stream()
                .filter(unit -> unit.getId() == unitId)
                .findFirst()
                .ifPresent(unit -> {
                    model.addAttribute("unitId", unit.getId());
                    model.addAttribute("currentUnitName", unit.getName());
                });
        model.addAttribute("checkIn", page);
        model.addAttribute("pageTitle", "Check-In");
        model.addAttribute("checkInBackUrl", actor != null ? "/?unit=" + unitId : "/login");
        return "berichte/anwesenheit-checkin";
    }

    private void requireBerichteModule(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.BERICHTE, unitId)) {
            throw new IllegalArgumentException("Check-In ist für diese Einheit nicht verfügbar.");
        }
        Unit unit = unitService.findActiveOrdered().stream()
                .filter(candidate -> candidate.getId() == unitId)
                .findFirst()
                .orElse(null);
        if (unit == null) {
            throw new IllegalArgumentException("Einheit nicht gefunden.");
        }
    }
}
