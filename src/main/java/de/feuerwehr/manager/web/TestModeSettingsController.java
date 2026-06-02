package de.feuerwehr.manager.web;

import de.feuerwehr.manager.settings.TestModeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/test-mode")
@RequiredArgsConstructor
public class TestModeSettingsController {

    private final TestModeService testModeService;

    @PostMapping("/enable")
    public String enable(RedirectAttributes redirectAttributes) {
        testModeService.enable();
        redirectAttributes.addFlashAttribute("saved", true);
        redirectAttributes.addFlashAttribute("message", "Testmodus ist aktiv. Neue und geänderte Fachdaten gelten nur als Testdaten.");
        return "redirect:/settings";
    }

    @PostMapping("/disable")
    public String disable(
            @RequestParam(defaultValue = "false") boolean deleteData, RedirectAttributes redirectAttributes) {
        testModeService.disable(deleteData);
        redirectAttributes.addFlashAttribute("saved", true);
        if (deleteData) {
            redirectAttributes.addFlashAttribute("message", "Testmodus beendet. Testdaten wurden gelöscht.");
        } else {
            redirectAttributes.addFlashAttribute(
                    "message",
                    "Testmodus beendet. Testdaten bleiben gespeichert und sind im Produktivbetrieb nicht sichtbar.");
        }
        return "redirect:/settings";
    }
}
