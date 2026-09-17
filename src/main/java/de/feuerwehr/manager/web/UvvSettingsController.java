package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.uvv.UnitUvvSettings;
import de.feuerwehr.manager.uvv.UvvCampaign;
import de.feuerwehr.manager.uvv.UvvCampaignStatus;
import de.feuerwehr.manager.uvv.UvvPresentationService;
import de.feuerwehr.manager.uvv.UvvService;
import de.feuerwehr.manager.uvv.UvvSettingsService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/uvv")
@RequiredArgsConstructor
public class UvvSettingsController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final UvvSettingsService uvvSettingsService;
    private final UvvService uvvService;
    private final UvvPresentationService uvvPresentationService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "campaign", required = false) Long campaignId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            Unit unit = unitService
                    .resolveActiveUnit(unitId, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
            accessControlService.requireUnitAccess(actor, unit.getId());
            requireModuleEnabled(unit.getId());
            UnitUvvSettings settings = uvvSettingsService.ensureSettings(unit.getId());
            List<User> unitUsers = uvvSettingsService.listSelectableUnitUsers(unit.getId());
            List<UvvCampaign> campaigns = uvvService.listCampaigns(unit.getId());
            model.addAttribute("unitId", unit.getId());
            model.addAttribute("currentUnitName", unit.getName());
            model.addAttribute("settings", settings);
            model.addAttribute("unitUsers", unitUsers);
            model.addAttribute("notificationUserIds", uvvSettingsService.parseNotificationUserIds(settings));
            model.addAttribute("campaigns", campaigns);
            model.addAttribute("attendanceOptions", uvvService.listAttendanceOptions(unit.getId()));
            model.addAttribute("campaignStatuses", UvvCampaignStatus.values());
            if (campaignId != null && campaignId > 0) {
                UvvService.CampaignDetail detail = uvvService.loadCampaignDetail(unit.getId(), campaignId);
                model.addAttribute("selectedCampaign", detail.campaign());
                model.addAttribute("campaignQuestions", detail.questions());
                model.addAttribute("campaignCompletions", detail.completions());
            }
            return "settings/uvv";
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId != null ? "redirect:/admin?scope=einheit&tab=module&unit=" + unitId : "redirect:/settings";
        }
    }

    @PostMapping
    public String saveSettings(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam int intervalMonths,
            @RequestParam int warnDays,
            @RequestParam(required = false, defaultValue = "false") boolean notifyPerson,
            @RequestParam(name = "notificationUserIds", required = false) Long[] notificationUserIds,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            uvvSettingsService.save(unit, intervalMonths, warnDays, notifyPerson, notificationUserIds);
            redirectAttributes.addFlashAttribute("message", "UVV-Einstellungen gespeichert.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings/uvv?unit=" + unit;
    }

    @PostMapping("/campaigns")
    public String createCampaign(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String title,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
            @RequestParam(required = false) String contentText,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            UvvCampaign created = uvvService.createCampaign(unit, title, eventDate, contentText);
            redirectAttributes.addFlashAttribute("message", "Kampagne angelegt.");
            return "redirect:/settings/uvv?unit=" + unit + "&campaign=" + created.getId();
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/uvv?unit=" + unit;
        }
    }

    @PostMapping("/campaigns/{campaignId}")
    public String updateCampaign(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long campaignId,
            @RequestParam long unit,
            @RequestParam String title,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
            @RequestParam(required = false) String contentText,
            @RequestParam UvvCampaignStatus status,
            @RequestParam(required = false) Long attendanceReportId,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            uvvService.updateCampaign(unit, campaignId, title, eventDate, contentText, status, attendanceReportId);
            redirectAttributes.addFlashAttribute("message", "Kampagne gespeichert.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings/uvv?unit=" + unit + "&campaign=" + campaignId;
    }

    @PostMapping("/campaigns/{campaignId}/presentation")
    public String uploadPresentation(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long campaignId,
            @RequestParam long unit,
            @RequestParam("file") MultipartFile file,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            UvvCampaign campaign = uvvPresentationService.storePresentation(unit, campaignId, file);
            redirectAttributes.addFlashAttribute(
                    "message",
                    "Präsentation hochgeladen (" + campaign.getPresentationPageCount() + " Folien).");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings/uvv?unit=" + unit + "&campaign=" + campaignId;
    }

    @PostMapping("/campaigns/{campaignId}/presentation/delete")
    public String deletePresentation(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long campaignId,
            @RequestParam long unit,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            uvvPresentationService.deletePresentation(unit, campaignId);
            redirectAttributes.addFlashAttribute("message", "Präsentation entfernt.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings/uvv?unit=" + unit + "&campaign=" + campaignId;
    }

    @GetMapping("/campaigns/{campaignId}/slides/{page}")
    public ResponseEntity<org.springframework.core.io.Resource> previewSlide(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long campaignId,
            @PathVariable int page,
            @RequestParam long unit) {
        accessControlService.requireAdminLevel(actor);
        accessControlService.requireUnitAccess(actor, unit);
        requireModuleEnabled(unit);
        UvvPresentationService.SlideFile slide = uvvPresentationService.loadSlide(unit, campaignId, page);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + slide.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(MediaType.parseMediaType(slide.mimeType()))
                .contentLength(slide.fileSize())
                .body(slide.resource());
    }

    @PostMapping("/campaigns/{campaignId}/questions")
    public String saveQuestions(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long campaignId,
            @RequestParam long unit,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            uvvService.replaceQuestions(unit, campaignId, parseQuestions(allParams));
            redirectAttributes.addFlashAttribute("message", "Fragen gespeichert.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings/uvv?unit=" + unit + "&campaign=" + campaignId;
    }

    @PostMapping("/campaigns/{campaignId}/delete")
    public String deleteCampaign(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long campaignId,
            @RequestParam long unit,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            uvvService.deleteCampaign(unit, campaignId);
            redirectAttributes.addFlashAttribute("message", "Kampagne gelöscht.");
            return "redirect:/settings/uvv?unit=" + unit;
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/uvv?unit=" + unit + "&campaign=" + campaignId;
        }
    }

    @PostMapping("/campaigns/{campaignId}/import-attendance")
    public String importAttendance(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long campaignId,
            @RequestParam long unit,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            int created = uvvService.importPresenceFromAttendance(unit, campaignId, actor);
            redirectAttributes.addFlashAttribute(
                    "message", created + " Anwesenheit(en) als UVV-Präsenz übernommen.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings/uvv?unit=" + unit + "&campaign=" + campaignId;
    }

    private List<UvvService.QuestionInput> parseQuestions(Map<String, String> params) {
        List<UvvService.QuestionInput> result = new ArrayList<>();
        for (int i = 0; i < UvvService.MAX_QUESTIONS; i++) {
            String text = params.get("q" + i + "_text");
            if (text == null || text.isBlank()) {
                continue;
            }
            result.add(new UvvService.QuestionInput(
                    text,
                    params.get("q" + i + "_a"),
                    params.get("q" + i + "_b"),
                    params.get("q" + i + "_c"),
                    params.get("q" + i + "_d"),
                    params.get("q" + i + "_correct")));
        }
        return result;
    }

    private void requireModuleEnabled(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.PERSONAL, unitId)) {
            throw new IllegalStateException("Das Modul Personal ist für diese Einheit nicht aktiviert.");
        }
    }
}
