package de.feuerwehr.manager.web;

import de.feuerwehr.manager.personal.MyAreaService;
import de.feuerwehr.manager.security.AppUserDetails;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/my-area")
@RequiredArgsConstructor
public class MyAreaController {

    private final MyAreaService myAreaService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            Model model,
            @RequestParam(required = false, defaultValue = "profile") String tab) {
        MyAreaService.MyAreaView view = myAreaService.loadView(actor.getUserId(), actor.getUnitId());
        String activeTab = normalizeTab(tab);
        if (view.person() == null && ("qualifications".equals(activeTab) || "lehrgaenge".equals(activeTab))) {
            return "redirect:/my-area?tab=profile";
        }
        model.addAttribute("displayName", actor.getDisplayName());
        model.addAttribute("username", actor.getUsername());
        model.addAttribute("myAreaTab", activeTab);
        model.addAttribute("person", view.person());
        model.addAttribute("emergencyContacts", view.emergencyContacts());
        model.addAttribute("completions", view.completions());
        model.addAttribute("hasLinkedPerson", view.person() != null);
        model.addAttribute("loginEmail", view.user().getLoginEmail());
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

    private static String normalizeTab(String tab) {
        String t = tab != null ? tab.trim().toLowerCase() : "";
        return switch (t) {
            case "qualifications" -> "qualifications";
            case "lehrgaenge" -> "lehrgaenge";
            default -> "profile";
        };
    }
}
