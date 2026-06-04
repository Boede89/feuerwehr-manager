package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserManagementService;
import de.feuerwehr.manager.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final UnitService unitService;
    private final UserManagementService userManagementService;
    private final UserRepository userRepository;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model) {
        unitService.resolveActiveUnit(unitId, actor).ifPresent(u -> model.addAttribute("unitId", u.getId()));
        User user = userRepository.findById(actor.getUserId()).orElseThrow();
        model.addAttribute("settingsUsername", user.getUsername());
        model.addAttribute("settingsDisplayName", user.getDisplayName());
        model.addAttribute(
                "diveraApiKeyConfigured",
                user.getDiveraApiKey() != null && !user.getDiveraApiKey().isBlank());
        return "settings";
    }

    @PostMapping("/account")
    public String updateAccount(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam String username,
            @RequestParam(required = false) String diveraApiKey,
            @RequestParam(required = false, defaultValue = "false") boolean clearDiveraApiKey,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            User updated = userManagementService.updateOwnAccount(
                    actor.getUserId(), username, diveraApiKey, clearDiveraApiKey, request);
            redirectAttributes.addFlashAttribute("saved", true);
            if (!updated.getUsername().equalsIgnoreCase(actor.getUsername())) {
                redirectAttributes.addFlashAttribute(
                        "message",
                        "Kontoeinstellungen gespeichert. Bitte beim nächsten Login den neuen Benutzernamen verwenden.");
            } else {
                redirectAttributes.addFlashAttribute("message", "Kontoeinstellungen gespeichert.");
            }
            return "redirect:/settings";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings";
        }
    }

    @PostMapping("/password")
    public String changePassword(
            @AuthenticationPrincipal AppUserDetails user,
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String newPasswordConfirm,
            RedirectAttributes redirectAttributes) {
        if (!newPassword.equals(newPasswordConfirm)) {
            redirectAttributes.addFlashAttribute("error", "Neue Passwörter stimmen nicht überein.");
            return "redirect:/settings";
        }
        try {
            userManagementService.changeOwnPassword(user.getUserId(), currentPassword, newPassword);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Passwort wurde geändert.");
            return "redirect:/settings";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings";
        }
    }
}
