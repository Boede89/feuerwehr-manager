package de.feuerwehr.manager.web;

import de.feuerwehr.manager.notification.NotificationChannel;
import de.feuerwehr.manager.notification.UserNotificationPreferenceService;
import de.feuerwehr.manager.notification.UserNotificationTopic;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.SecurityProperties;
import de.feuerwehr.manager.security.TotpSessionKeys;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserManagementService;
import de.feuerwehr.manager.user.UserRepository;
import de.feuerwehr.manager.user.UserTotpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final UnitService unitService;
    private final UserManagementService userManagementService;
    private final UserRepository userRepository;
    private final UserTotpService userTotpService;
    private final UserNotificationPreferenceService userNotificationPreferenceService;
    private final SecurityProperties securityProperties;

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
        model.addAttribute("rfidLoginEnabled", securityProperties.rfidApiEnabled());
        model.addAttribute("rfidCards", userManagementService.listRfidCards(actor.getUserId()));
        return "settings";
    }

    @GetMapping("/notifications")
    public String notifications(
            @AuthenticationPrincipal AppUserDetails actor, Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("notificationTopics", userNotificationPreferenceService.buildSettingsView(actor.getUserId()));
            model.addAttribute("notificationChannels", NotificationChannel.values());
            return "settings/notifications";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings";
        }
    }

    @PostMapping("/notifications")
    public String saveNotifications(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam Map<String, String> params,
            RedirectAttributes redirectAttributes) {
        try {
            Map<UserNotificationTopic, Boolean> emailEnabled = new EnumMap<>(UserNotificationTopic.class);
            for (UserNotificationTopic topic : UserNotificationTopic.values()) {
                String key = "email_" + topic.paramKey();
                emailEnabled.put(topic, "true".equalsIgnoreCase(params.get(key)));
            }
            userNotificationPreferenceService.saveEmailPreferences(actor.getUserId(), emailEnabled);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Benachrichtigungseinstellungen gespeichert.");
            return "redirect:/settings/notifications";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/notifications";
        }
    }

    @GetMapping("/totp/qr")
    public ResponseEntity<byte[]> totpQr(@AuthenticationPrincipal AppUserDetails actor, HttpSession session) {
        try {
            byte[] png = userTotpService.qrImageForSession(actor.getUserId(), session);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.IMAGE_PNG)
                    .body(png);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
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

    @PostMapping("/rfid")
    public String addOwnRfid(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam String cardUid,
            @RequestParam(required = false) String label,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            userManagementService.registerOwnRfidCard(actor.getUserId(), cardUid, label, request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "RFID-Chip wurde registriert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings";
    }

    @PostMapping("/rfid/{cardId}/revoke")
    public String revokeOwnRfid(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long cardId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            userManagementService.revokeOwnRfidCard(actor.getUserId(), cardId, request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "RFID-Chip wurde gesperrt.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings";
    }

    @PostMapping("/rfid/{cardId}/reactivate")
    public String reactivateRfid(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long cardId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            userManagementService.reactivateRfidCard(cardId, actor, request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "RFID-Chip wurde entsperrt.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings";
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
