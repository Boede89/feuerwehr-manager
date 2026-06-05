package de.feuerwehr.manager.web;

import de.feuerwehr.manager.atemschutz.AtemschutzEmailTemplate;
import de.feuerwehr.manager.atemschutz.AtemschutzSettingsService;
import de.feuerwehr.manager.atemschutz.UnitAtemschutzSettings;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.user.User;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/atemschutz")
@RequiredArgsConstructor
public class AtemschutzSettingsController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final AtemschutzSettingsService atemschutzSettingsService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "tab", defaultValue = "warnschwelle") String tab,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            Unit unit = unitService
                    .resolveActiveUnit(unitId, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
            accessControlService.requireUnitAccess(actor, unit.getId());
            requireModuleEnabled(unit.getId());
            UnitAtemschutzSettings settings = atemschutzSettingsService.ensureSettings(unit.getId());
            List<User> unitUsers = atemschutzSettingsService.listSelectableUnitUsers(unit.getId());
            List<AtemschutzEmailTemplate> templates = atemschutzSettingsService.listEmailTemplates(unit.getId());
            model.addAttribute("unitId", unit.getId());
            model.addAttribute("currentUnitName", unit.getName());
            model.addAttribute("activeTab", normalizeTab(tab));
            model.addAttribute("settings", settings);
            model.addAttribute("unitUsers", unitUsers);
            model.addAttribute("notificationUserIds", atemschutzSettingsService.parseNotificationUserIds(settings));
            model.addAttribute("ccUserIds", atemschutzSettingsService.parseCcUserIds(settings));
            model.addAttribute("emailTemplates", templates);
            return "settings/atemschutz";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId != null ? "redirect:/admin?scope=einheit&tab=module&unit=" + unitId : "redirect:/settings";
        }
    }

    @PostMapping("/warnschwelle")
    public String saveWarnschwelle(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam int warnDays,
            @RequestParam(defaultValue = "AGT") String agtCourseName,
            RedirectAttributes redirectAttributes) {
        return save(actor, unit, "warnschwelle", redirectAttributes, () -> {
            atemschutzSettingsService.saveWarnschwelle(unit, warnDays, agtCourseName);
            redirectAttributes.addFlashAttribute("message", "Warnschwelle gespeichert.");
        });
    }

    @PostMapping("/notifications")
    public String saveNotifications(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "notificationUserIds", required = false) Long[] notificationUserIds,
            RedirectAttributes redirectAttributes) {
        return save(actor, unit, "benachrichtigungen", redirectAttributes, () -> {
            List<Long> ids = notificationUserIds == null ? List.of() : Arrays.asList(notificationUserIds);
            atemschutzSettingsService.saveNotificationUsers(unit, ids);
            redirectAttributes.addFlashAttribute("message", "Benachrichtigungen gespeichert.");
        });
    }

    @PostMapping("/cc")
    public String saveCc(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "ccUserIds", required = false) Long[] ccUserIds,
            RedirectAttributes redirectAttributes) {
        return save(actor, unit, "cc", redirectAttributes, () -> {
            List<Long> ids = ccUserIds == null ? List.of() : Arrays.asList(ccUserIds);
            atemschutzSettingsService.saveCcUsers(unit, ids);
            redirectAttributes.addFlashAttribute("message", "CC-Empfänger gespeichert.");
        });
    }

    @PostMapping("/email-templates")
    public String saveEmailTemplates(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {
        return save(actor, unit, "email", redirectAttributes, () -> {
            int saved = 0;
            for (String key : allParams.keySet()) {
                if (!key.startsWith("subject_")) {
                    continue;
                }
                String templateKey = key.substring("subject_".length());
                String subject = allParams.get(key);
                String body = allParams.get("body_" + templateKey);
                if (body == null) {
                    continue;
                }
                atemschutzSettingsService.saveEmailTemplate(unit, templateKey, subject, body);
                saved++;
            }
            redirectAttributes.addFlashAttribute(
                    "message", saved > 0 ? saved + " E-Mail-Vorlage(n) gespeichert." : "Gespeichert.");
        });
    }

    private String save(
            AppUserDetails actor,
            long unitId,
            String tab,
            RedirectAttributes redirectAttributes,
            Runnable action) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unitId);
            requireModuleEnabled(unitId);
            action.run();
            redirectAttributes.addFlashAttribute("saved", true);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings/atemschutz?unit=" + unitId + "&tab=" + tab;
    }

    private void requireModuleEnabled(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.ATEMSCHUTZ, unitId)) {
            throw new IllegalArgumentException("Das Modul Atemschutz ist für diese Einheit nicht aktiviert.");
        }
    }

    private static String normalizeTab(String tab) {
        return switch (tab) {
            case "email", "benachrichtigungen", "cc" -> tab;
            default -> "warnschwelle";
        };
    }
}
