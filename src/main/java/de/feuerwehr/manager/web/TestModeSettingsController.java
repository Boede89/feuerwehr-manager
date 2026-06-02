package de.feuerwehr.manager.web;

import de.feuerwehr.manager.settings.TestModeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/test-mode")
@PreAuthorize("hasRole('SUPER_ADMIN')")
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
    public String disable(RedirectAttributes redirectAttributes) {
        testModeService.disable();
        redirectAttributes.addFlashAttribute("saved", true);
        redirectAttributes.addFlashAttribute("message", "Testmodus beendet. Alle Teständerungen wurden verworfen.");
        return "redirect:/settings";
    }
}
