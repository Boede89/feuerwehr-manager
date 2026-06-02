package de.feuerwehr.manager.web;

import de.feuerwehr.manager.divera.DiveraService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final UnitService unitService;
    private final DiveraService diveraService;

    @GetMapping("/")
    public String dashboard(
            @AuthenticationPrincipal AppUserDetails currentUser,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model) {
        model.addAttribute("currentUser", currentUser);
        if (unitService.findActiveOrdered().isEmpty()) {
            return "redirect:/settings/units?setup=1";
        }
        Optional<Unit> unit = unitService.resolveActiveUnit(unitId);
        if (unit.isEmpty()) {
            return "redirect:/settings/units?setup=1";
        }
        long resolvedId = unit.get().getId();
        model.addAttribute("units", unitService.findActiveOrdered());
        model.addAttribute("unitId", resolvedId);
        model.addAttribute("currentUnitName", unit.get().getName());
        model.addAttribute("divera", diveraService.getAlarmsForUnit(resolvedId));
        return "dashboard";
    }
}
