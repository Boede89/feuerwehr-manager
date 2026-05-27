package de.feuerwehr.manager.web;

import de.feuerwehr.manager.divera.DiveraService;
import de.feuerwehr.manager.unit.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final UnitRepository unitRepository;
    private final DiveraService diveraService;

    @GetMapping("/")
    public String dashboard(@RequestParam(name = "unit", defaultValue = "1") long unitId, Model model) {
        model.addAttribute("units", unitRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("unitId", unitId);
        model.addAttribute("divera", diveraService.getAlarmsForUnit(unitId));
        return "dashboard";
    }
}
