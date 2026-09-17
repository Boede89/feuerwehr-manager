package de.feuerwehr.manager.web;

import de.feuerwehr.manager.mediathek.MediathekStorageService;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/mediathek")
@RequiredArgsConstructor
public class MediathekSettingsController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            Unit unit = unitService
                    .resolveActiveUnit(unitId, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
            accessControlService.requireUnitAccess(actor, unit.getId());
            if (!moduleSettingsService.isEnabled(AppModule.MEDIATHEK, unit.getId())) {
                throw new IllegalArgumentException("Das Modul Mediathek ist für diese Einheit nicht aktiviert.");
            }
            model.addAttribute("unitId", unit.getId());
            model.addAttribute("currentUnitName", unit.getName());
            model.addAttribute("pageTitle", "Mediathek – Einstellungen");
            model.addAttribute("maxFileSizeMb", MediathekStorageService.MAX_FILE_SIZE / (1024L * 1024L));
            return "settings/mediathek";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId != null ? "redirect:/admin?scope=einheit&tab=module&unit=" + unitId : "redirect:/admin";
        }
    }
}
