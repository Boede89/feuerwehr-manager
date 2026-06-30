package de.feuerwehr.manager.web;

import de.feuerwehr.manager.einsatzapp.EinsatzAppSettingsService;
import de.feuerwehr.manager.einsatzapp.FcmConfigService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/einsatzapp")
@RequiredArgsConstructor
public class EinsatzAppSettingsController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final EinsatzAppSettingsService einsatzAppSettingsService;
    private final FcmConfigService fcmConfigService;
    private final FcmAccessTokenProvider fcmAccessTokenProvider;

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
            requireModuleEnabled(unit.getId());
            model.addAttribute("unitId", unit.getId());
            model.addAttribute("currentUnitName", unit.getName());
            model.addAttribute("pushEnabled", einsatzAppSettingsService.isPushEnabled(unit.getId()));
            model.addAttribute("fcmConfigured", fcmConfigService.isConfigured());
            model.addAttribute("fcmUploaded", fcmConfigService.hasUploadedServiceAccount());
            model.addAttribute("fcmProjectId", fcmConfigService.configuredProjectId().orElse(null));
            model.addAttribute("fcmClientEmail", fcmConfigService.configuredClientEmail().orElse(null));
            model.addAttribute("deviceCount", einsatzAppSettingsService.countDevices(unit.getId()));
            return "settings/einsatzapp";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId != null
                    ? "redirect:/admin?scope=einheit&tab=module&unit=" + unitId
                    : "redirect:/admin?scope=einheit&tab=module";
        }
    }

    @PostMapping
    public String savePush(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(required = false, defaultValue = "false") boolean pushEnabled,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            einsatzAppSettingsService.savePushEnabled(unit, pushEnabled);
            redirectAttributes.addFlashAttribute("message", "Push-Einstellungen gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings/einsatzapp?unit=" + unit;
    }

    @PostMapping("/fcm-upload")
    public String uploadFcmServiceAccount(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam("serviceAccountFile") MultipartFile serviceAccountFile,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            fcmConfigService.saveUploadedServiceAccount(serviceAccountFile);
            fcmAccessTokenProvider.invalidateCache();
            redirectAttributes.addFlashAttribute("message", "Firebase-Dienstkonto gespeichert. Push-Versand ist bereit.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Upload fehlgeschlagen: " + e.getMessage());
        }
        return "redirect:/settings/einsatzapp?unit=" + unit;
    }

    @PostMapping("/fcm-delete")
    public String deleteFcmServiceAccount(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            fcmConfigService.deleteUploadedServiceAccount();
            fcmAccessTokenProvider.invalidateCache();
            redirectAttributes.addFlashAttribute("message", "Hochgeladenes Dienstkonto entfernt.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Entfernen fehlgeschlagen: " + e.getMessage());
        }
        return "redirect:/settings/einsatzapp?unit=" + unit;
    }

    private void requireModuleEnabled(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.EINSATZAPP, unitId)) {
            throw new IllegalArgumentException("Das Modul Einsatz-App ist für diese Einheit nicht aktiviert.");
        }
    }
}
