package de.feuerwehr.manager.web;

import de.feuerwehr.manager.atemschutz.AtemschutzNotificationSectionView;
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
import java.util.HashMap;
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
            List<AtemschutzNotificationSectionView> sections =
                    atemschutzSettingsService.buildNotificationSections(unit.getId());
            model.addAttribute("unitId", unit.getId());
            model.addAttribute("currentUnitName", unit.getName());
            model.addAttribute("settings", settings);
            model.addAttribute("unitUsers", unitUsers);
            model.addAttribute("notificationSections", sections);
            model.addAttribute("instructorUserIds", atemschutzSettingsService.parseInstructorUserIds(settings));
            model.addAttribute("unitCourses", atemschutzSettingsService.listSelectableCourses(unit.getId()));
            model.addAttribute("selectedAgtCourseId", atemschutzSettingsService.selectedAgtCourseUiId(unit.getId()));
            return "settings/atemschutz";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId != null ? "redirect:/admin?scope=einheit&tab=module&unit=" + unitId : "redirect:/settings";
        }
    }

    @PostMapping("/agt")
    public String saveAgt(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long agtCourseId,
            RedirectAttributes redirectAttributes) {
        return save(actor, unit, redirectAttributes, () -> {
            atemschutzSettingsService.saveAgtCourse(unit, agtCourseId);
            redirectAttributes.addFlashAttribute("message", "Lehrgang gespeichert.");
        });
    }

    @PostMapping("/instructors")
    public String saveInstructors(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "instructorUserIds", required = false) Long[] instructorUserIds,
            RedirectAttributes redirectAttributes) {
        return save(actor, unit, redirectAttributes, () -> {
            List<Long> ids = instructorUserIds == null ? List.of() : Arrays.asList(instructorUserIds);
            atemschutzSettingsService.saveInstructors(unit, ids);
            redirectAttributes.addFlashAttribute("message", "Atemschutzausbilder gespeichert.");
        });
    }

    @PostMapping("/notifications")
    public String saveNotifications(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam int g26WarnDays,
            @RequestParam int streckeWarnDays,
            @RequestParam int uebungWarnDays,
            @RequestParam(name = "g26NotifyInstructors", defaultValue = "false") boolean g26NotifyInstructors,
            @RequestParam(name = "streckeNotifyInstructors", defaultValue = "false") boolean streckeNotifyInstructors,
            @RequestParam(name = "uebungNotifyInstructors", defaultValue = "false") boolean uebungNotifyInstructors,
            @RequestParam(name = "g26CcUserIds", required = false) Long[] g26CcUserIds,
            @RequestParam(name = "streckeCcUserIds", required = false) Long[] streckeCcUserIds,
            @RequestParam(name = "uebungCcUserIds", required = false) Long[] uebungCcUserIds,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {
        return save(actor, unit, redirectAttributes, () -> {
            Map<String, String> subjects = new HashMap<>();
            Map<String, String> bodies = new HashMap<>();
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("subject_")) {
                    subjects.put(key.substring("subject_".length()), entry.getValue());
                } else if (key.startsWith("body_")) {
                    bodies.put(key.substring("body_".length()), entry.getValue());
                }
            }
            atemschutzSettingsService.saveNotificationSettings(
                    unit,
                    g26WarnDays,
                    streckeWarnDays,
                    uebungWarnDays,
                    g26NotifyInstructors,
                    streckeNotifyInstructors,
                    uebungNotifyInstructors,
                    g26CcUserIds == null ? List.of() : Arrays.asList(g26CcUserIds),
                    streckeCcUserIds == null ? List.of() : Arrays.asList(streckeCcUserIds),
                    uebungCcUserIds == null ? List.of() : Arrays.asList(uebungCcUserIds),
                    subjects,
                    bodies);
            redirectAttributes.addFlashAttribute("message", "Benachrichtigungen gespeichert.");
        });
    }

    private String save(
            AppUserDetails actor,
            long unitId,
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
        return "redirect:/settings/atemschutz?unit=" + unitId;
    }

    private void requireModuleEnabled(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.ATEMSCHUTZ, unitId)) {
            throw new IllegalArgumentException("Das Modul Atemschutz ist für diese Einheit nicht aktiviert.");
        }
    }
}
