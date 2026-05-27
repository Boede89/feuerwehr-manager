package de.feuerwehr.manager.web;

import de.feuerwehr.manager.unit.UnitDiveraSettings;
import de.feuerwehr.manager.unit.UnitDiveraSettingsRepository;
import de.feuerwehr.manager.unit.UnitRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/divera")
@RequiredArgsConstructor
public class DiveraSettingsController {

    private final UnitRepository unitRepository;
    private final UnitDiveraSettingsRepository diveraSettingsRepository;

    @GetMapping
    public String form(@RequestParam(name = "unit", defaultValue = "1") long unitId, Model model) {
        model.addAttribute("units", unitRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("unitId", unitId);
        Optional<UnitDiveraSettings> opt = diveraSettingsRepository.findByUnitId(unitId);
        if (opt.isPresent()) {
            UnitDiveraSettings s = opt.get();
            model.addAttribute("apiBaseUrl", s.getApiBaseUrl());
            model.addAttribute("accessKeyConfigured", s.getAccessKey() != null && !s.getAccessKey().isBlank());
        } else {
            model.addAttribute("apiBaseUrl", "https://app.divera247.com");
            model.addAttribute("accessKeyConfigured", false);
        }
        return "settings-divera";
    }

    @PostMapping
    public String save(
            @RequestParam(name = "unit", defaultValue = "1") long unitId,
            @RequestParam String apiBaseUrl,
            @RequestParam(required = false) String accessKey,
            RedirectAttributes redirectAttributes) {

        UnitDiveraSettings settings =
                diveraSettingsRepository.findByUnitId(unitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden"));

        String base = apiBaseUrl == null ? "" : apiBaseUrl.trim();
        if (base.isEmpty()) {
            base = "https://app.divera247.com";
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        settings.setApiBaseUrl(base);

        if (accessKey != null && !accessKey.isBlank()) {
            settings.setAccessKey(accessKey.trim().replaceAll("[\\r\\n\\t\\v]+", ""));
        }

        diveraSettingsRepository.save(settings);
        redirectAttributes.addFlashAttribute("saved", true);
        return "redirect:/settings/divera?unit=" + unitId;
    }
}
