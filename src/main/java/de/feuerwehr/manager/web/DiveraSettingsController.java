package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitDiveraSettings;
import de.feuerwehr.manager.unit.UnitDiveraSettingsRepository;
import de.feuerwehr.manager.unit.UnitService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/divera")
@RequiredArgsConstructor
public class DiveraSettingsController {

    private final UnitService unitService;
    private final UnitDiveraSettingsRepository diveraSettingsRepository;

    @GetMapping
    public String form(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId) {
        if (unitService.findActiveOrdered(actor).isEmpty()) {
            return "redirect:/settings/units?setup=1";
        }
        Optional<Unit> unit = unitService.resolveActiveUnit(unitId, actor);
        if (unit.isEmpty()) {
            return "redirect:/settings/units?setup=1";
        }
        return "redirect:/admin?scope=einheit&tab=schnittstellen&unit=" + unit.get().getId();
    }

    @PostMapping
    public String save(
            @RequestParam(name = "unit") long unitId,
            @RequestParam(required = false) String accessKey,
            @RequestParam(required = false) String webhookSecret,
            RedirectAttributes redirectAttributes) {

        UnitDiveraSettings settings = diveraSettingsRepository
                .findByUnitId(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Keine Divera-Einstellungen für diese Einheit."));

        settings.setApiBaseUrl("https://app.divera247.com");
        if (accessKey != null && !accessKey.isBlank()) {
            settings.setAccessKey(accessKey.trim().replaceAll("[\\r\\n\\t\\v]+", ""));
        }
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            settings.setWebhookSecret(webhookSecret.trim());
        }

        diveraSettingsRepository.save(settings);
        redirectAttributes.addFlashAttribute("saved", true);
        return "redirect:/admin?scope=einheit&tab=schnittstellen&unit=" + unitId;
    }
}
