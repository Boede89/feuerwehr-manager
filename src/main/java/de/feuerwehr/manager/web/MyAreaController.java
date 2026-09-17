package de.feuerwehr.manager.web;

import de.feuerwehr.manager.drivinglicense.DrivingLicenseClass;
import de.feuerwehr.manager.drivinglicense.DrivingLicensePresence;
import de.feuerwehr.manager.drivinglicense.DrivingLicenseService;
import de.feuerwehr.manager.personal.MyAreaService;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.ApplicationSettings;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.uvv.UvvPresentationService;
import de.feuerwehr.manager.uvv.UvvService;
import java.time.LocalDate;
import java.util.HashMap;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/my-area")
@RequiredArgsConstructor
public class MyAreaController {

    private final MyAreaService myAreaService;
    private final GlobalSettingsService globalSettingsService;
    private final UvvService uvvService;
    private final UvvPresentationService uvvPresentationService;
    private final DrivingLicenseService drivingLicenseService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            Model model,
            @RequestParam(required = false, defaultValue = "profile") String tab) {
        MyAreaService.MyAreaView view = myAreaService.loadView(actor.getUserId(), actor.getUnitId());
        ApplicationSettings global = globalSettingsService.get();
        String activeTab = normalizeTab(tab, view.person() != null);
        if (view.person() == null
                && ("lehrgaenge".equals(activeTab) || "uvv".equals(activeTab) || "fuehrerschein".equals(activeTab))) {
            return "redirect:/my-area?tab=profile";
        }
        model.addAttribute("displayName", actor.getDisplayName());
        model.addAttribute("username", actor.getUsername());
        model.addAttribute("myAreaTab", activeTab);
        model.addAttribute("person", view.person());
        model.addAttribute("emergencyContacts", view.emergencyContacts());
        model.addAttribute("completions", view.completions());
        model.addAttribute("hasLinkedPerson", view.person() != null);
        model.addAttribute("loginEmail", myAreaService.resolveContactEmail(actor.getUserId(), actor.getUnitId()));
        model.addAttribute("privacyContactName", global.getPrivacyContactName());
        model.addAttribute("privacyContactEmail", global.getPrivacyContactEmail());
        model.addAttribute("privacyContactPhone", global.getPrivacyContactPhone());
        if (view.person() != null) {
            UvvService.OnlineCampaignView uvvOnline = uvvService.loadOnlineForPerson(view.person().getId());
            model.addAttribute("uvvOnline", uvvOnline);
            model.addAttribute("uvvAvailable", uvvOnline.available());
            if ("fuehrerschein".equals(activeTab)) {
                DrivingLicenseService.PersonLicenseView licenseView =
                        drivingLicenseService.loadPersonView(view.person().getId());
                model.addAttribute("drivingLicense", licenseView.license());
                model.addAttribute("licenseChecks", licenseView.checks());
                model.addAttribute("licenseLevel", licenseView.level());
                model.addAttribute("licenseIntervalMonths", licenseView.intervalMonths());
                model.addAttribute("licenseWarnDays", licenseView.warnDays());
                model.addAttribute("licenseClasses", licenseView.availableClasses());
                model.addAttribute(
                        "selectedLicenseClasses",
                        licenseView.license() != null
                                ? DrivingLicenseClass.parseCsv(licenseView.license().getClassesCsv())
                                : java.util.Set.of());
                model.addAttribute("licensePresences", DrivingLicensePresence.values());
            }
        } else {
            model.addAttribute("uvvAvailable", false);
        }
        return "my-area";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportData(@AuthenticationPrincipal AppUserDetails actor) {
        byte[] body = myAreaService.exportUserData(actor.getUserId(), actor.getUnitId());
        String filename = "daten-export-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @PostMapping("/contact")
    public String saveContact(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String loginEmail,
            @RequestParam(required = false) String address,
            RedirectAttributes redirectAttributes) {
        try {
            myAreaService.updateContact(actor.getUserId(), actor.getUnitId(), phone, loginEmail, address);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Kontaktdaten gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/my-area?tab=profile";
    }

    @PostMapping("/emergency-contacts")
    public String createEmergencyContact(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam String name,
            @RequestParam String phone,
            @RequestParam(required = false) String relationship,
            RedirectAttributes redirectAttributes) {
        try {
            myAreaService.createEmergencyContact(actor.getUserId(), actor.getUnitId(), name, phone, relationship);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Notfallkontakt gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/my-area?tab=profile";
    }

    @PostMapping("/emergency-contacts/update")
    public String updateEmergencyContact(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long contactId,
            @RequestParam String name,
            @RequestParam String phone,
            @RequestParam(required = false) String relationship,
            RedirectAttributes redirectAttributes) {
        try {
            myAreaService.updateEmergencyContact(
                    actor.getUserId(), actor.getUnitId(), contactId, name, phone, relationship);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Notfallkontakt gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/my-area?tab=profile";
    }

    @PostMapping("/emergency-contacts/delete")
    public String deleteEmergencyContact(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long contactId,
            RedirectAttributes redirectAttributes) {
        try {
            myAreaService.deleteEmergencyContact(actor.getUserId(), actor.getUnitId(), contactId);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Notfallkontakt gelöscht.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/my-area?tab=profile";
    }

    @PostMapping("/driving-license")
    public String saveOwnDrivingLicense(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(defaultValue = "UNKNOWN") DrivingLicensePresence presence,
            @RequestParam(name = "classes", required = false) String[] classes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedOn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresOn,
            @RequestParam(required = false) String numberSuffix,
            @RequestParam(required = false) String restrictions,
            @RequestParam(required = false, defaultValue = "false") boolean selfReportAcknowledged,
            RedirectAttributes redirectAttributes) {
        try {
            Person person = myAreaService.loadView(actor.getUserId(), actor.getUnitId()).person();
            if (person == null) {
                throw new IllegalArgumentException(
                        "Ihrem Benutzerkonto ist keine Person zugeordnet. Bitte wenden Sie sich an die Verwaltung.");
            }
            drivingLicenseService.saveStammdaten(
                    person.getId(),
                    presence,
                    classes,
                    issuedOn,
                    expiresOn,
                    numberSuffix,
                    restrictions,
                    selfReportAcknowledged);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute(
                    "message",
                    "Führerschein-Angaben gespeichert. Der Eintrag gilt erst nach einer Kontrolle durch die Verwaltung.");
            return "redirect:/my-area?tab=fuehrerschein";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/my-area?tab=fuehrerschein";
        }
    }

    @GetMapping("/uvv/campaigns/{campaignId}/slides/{page}")
    public ResponseEntity<org.springframework.core.io.Resource> uvvSlide(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long campaignId,
            @PathVariable int page) {
        Person person = myAreaService.loadView(actor.getUserId(), actor.getUnitId()).person();
        if (person == null) {
            throw new IllegalArgumentException(
                    "Ihrem Benutzerkonto ist keine Person zugeordnet. Bitte wenden Sie sich an die Verwaltung.");
        }
        long unitId = person.getUnit().getId();
        uvvService.requireCampaign(unitId, campaignId);
        UvvPresentationService.SlideFile slide = uvvPresentationService.loadSlide(unitId, campaignId, page);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + slide.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(MediaType.parseMediaType(slide.mimeType()))
                .contentLength(slide.fileSize())
                .body(slide.resource());
    }

    @PostMapping("/uvv/complete")
    public String completeUvvOnline(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long campaignId,
            @RequestParam(required = false, defaultValue = "false") boolean confirmed,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {
        try {
            Person person = myAreaService
                    .loadView(actor.getUserId(), actor.getUnitId())
                    .person();
            if (person == null) {
                throw new IllegalArgumentException(
                        "Ihrem Benutzerkonto ist keine Person zugeordnet. Bitte wenden Sie sich an die Verwaltung.");
            }
            boolean presentationCompleted = Boolean.parseBoolean(
                    allParams.getOrDefault("presentationCompleted", "false"));
            uvvService.completeOnline(
                    person.getId(),
                    campaignId,
                    parseAnswers(allParams),
                    confirmed,
                    presentationCompleted,
                    actor);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "UVV-/Kraftfahrer-Belehrung als erledigt gespeichert.");
            return "redirect:/my-area?tab=uvv";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/my-area?tab=uvv";
        }
    }

    private static Map<Long, String> parseAnswers(Map<String, String> params) {
        Map<Long, String> answers = new HashMap<>();
        if (params == null) {
            return answers;
        }
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith("answer_")) {
                continue;
            }
            try {
                long questionId = Long.parseLong(key.substring("answer_".length()));
                answers.put(questionId, entry.getValue());
            } catch (NumberFormatException ignored) {
                // skip malformed keys
            }
        }
        return answers;
    }

    private static String normalizeTab(String tab, boolean hasLinkedPerson) {
        String t = tab != null ? tab.trim().toLowerCase() : "";
        return switch (t) {
            case "qualifications", "lehrgaenge" -> hasLinkedPerson ? "lehrgaenge" : "profile";
            case "uvv" -> hasLinkedPerson ? "uvv" : "profile";
            case "fuehrerschein", "fuhrerschein" -> hasLinkedPerson ? "fuehrerschein" : "profile";
            default -> "profile";
        };
    }
}
