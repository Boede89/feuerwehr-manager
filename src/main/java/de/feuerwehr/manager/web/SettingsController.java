package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.TotpSessionKeys;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserManagementService;
import de.feuerwehr.manager.user.UserRepository;
import de.feuerwehr.manager.user.UserTotpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final UnitService unitService;
    private final UserManagementService userManagementService;
    private final UserRepository userRepository;
    private final UserTotpService userTotpService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "totpDisable", required = false) Boolean totpDisable,
            HttpSession session,
            Model model) {
        unitService.resolveActiveUnit(unitId, actor).ifPresent(u -> model.addAttribute("unitId", u.getId()));
        User user = userRepository.findById(actor.getUserId()).orElseThrow();
        model.addAttribute("settingsUsername", user.getUsername());
        model.addAttribute("settingsDisplayName", user.getDisplayName());
        model.addAttribute(
                "diveraApiKeyConfigured",
                user.getDiveraApiKey() != null && !user.getDiveraApiKey().isBlank());
        model.addAttribute("totpEnabled", user.isTotpEnabled());
        Object setupUri = session.getAttribute(TotpSessionKeys.SETUP_OTPAUTH_URI);
        model.addAttribute("totpSetupActive", setupUri != null);
        if (setupUri != null) {
            model.addAttribute("totpSetupUri", setupUri.toString());
        }
        model.addAttribute("totpDisableActive", Boolean.TRUE.equals(totpDisable));
        return "settings";
    }

    @GetMapping("/totp/qr")
    public ResponseEntity<byte[]> totpQr(@AuthenticationPrincipal AppUserDetails actor, HttpSession session) {
        byte[] png = userTotpService.qrImageForSession(actor.getUserId(), session);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    @PostMapping("/totp/setup")
    public String totpSetup(
            @AuthenticationPrincipal AppUserDetails actor,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            userTotpService.beginSetup(actor.getUserId(), session);
            return "redirect:/settings";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings";
        }
    }

    @PostMapping("/totp/cancel")
    public String totpCancelSetup(@AuthenticationPrincipal AppUserDetails actor, HttpSession session) {
        userTotpService.cancelSetup(actor.getUserId(), session);
        return "redirect:/settings";
    }

    @PostMapping("/totp/confirm")
    public String totpConfirm(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam String code,
            HttpServletRequest request,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            userTotpService.confirmSetup(actor.getUserId(), code, request, session);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "2FA erfolgreich aktiviert.");
            return "redirect:/settings";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings";
        }
    }

    @PostMapping("/totp/disable")
    public String totpDisable(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam String code,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            userTotpService.disable(actor.getUserId(), code, request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "2FA deaktiviert.");
            return "redirect:/settings";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings?totpDisable=1";
        }
    }

    @PostMapping("/theme")
    public ResponseEntity<Void> updateTheme(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam String theme,
            HttpServletRequest request) {
        try {
            userManagementService.updateOwnTheme(actor.getUserId(), theme, request);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
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
