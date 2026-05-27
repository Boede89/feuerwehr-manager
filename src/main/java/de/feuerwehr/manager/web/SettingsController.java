package de.feuerwehr.manager.web;

import de.feuerwehr.manager.unit.UnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final UnitService unitService;

    @GetMapping
    public String index(@RequestParam(name = "unit", required = false) Long unitId, Model model) {
        unitService.resolveActiveUnit(unitId).ifPresent(u -> {
            model.addAttribute("unitId", u.getId());
            model.addAttribute("hasActiveUnit", true);
        });
        if (!model.containsAttribute("hasActiveUnit")) {
            model.addAttribute("hasActiveUnit", false);
        }
        return "settings";
    }
}
