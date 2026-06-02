package de.feuerwehr.manager.web;

import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/units")
@RequiredArgsConstructor
public class UnitSettingsController {

    private final UnitService unitService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("units", unitService.findAllOrdered());
        model.addAttribute("activeUnits", unitService.findActiveOrdered());
        return "settings-units";
    }

    @PostMapping
    public String create(@RequestParam String name, RedirectAttributes redirectAttributes) {
        try {
            Unit created = unitService.create(name);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Einheit „" + created.getName() + "“ wurde angelegt.");
            return "redirect:/settings/units";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/units";
        }
    }

    @GetMapping("/{id}")
    public String editForm(@PathVariable long id, Model model) {
        return unitService
                .findById(id)
                .map(unit -> {
                    model.addAttribute("unit", unit);
                    return "settings-unit-edit";
                })
                .orElse("redirect:/settings/units");
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable long id,
            @RequestParam String name,
            @RequestParam(required = false) String active,
            RedirectAttributes redirectAttributes) {
        boolean isActive = "true".equalsIgnoreCase(active);
        try {
            unitService.update(id, name, isActive);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Einheit wurde gespeichert.");
            return "redirect:/settings/units";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/units/" + id;
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirectAttributes) {
        try {
            unitService.delete(id);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Einheit wurde gelöscht.");
            return "redirect:/settings/units";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/units/" + id;
        }
    }
}
