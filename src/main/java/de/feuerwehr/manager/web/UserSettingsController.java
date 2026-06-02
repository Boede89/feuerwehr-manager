package de.feuerwehr.manager.web;

import de.feuerwehr.manager.dsgvo.AuditEventType;
import de.feuerwehr.manager.dsgvo.AuditService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserManagementService;
import de.feuerwehr.manager.user.UserRole;
import de.feuerwehr.manager.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/settings/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserManagementService userManagementService;
    private final UserService userService;
    private final AuditService auditService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userManagementService.listAccounts());
        model.addAttribute("roles", UserRole.values());
        return "settings-users";
    }

    @PostMapping
    public String create(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam String username,
            @RequestParam String displayName,
            @RequestParam String password,
            @RequestParam(defaultValue = "USER") UserRole role,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            User created = userManagementService.createUser(
                    username, displayName, password, role, actor.getUserId(), request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute(
                    "message", "Benutzer „" + created.getUsername() + "“ wurde angelegt.");
            return "redirect:/settings/users";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/users";
        }
    }

    @GetMapping("/{id}")
    public String editForm(@PathVariable long id, Model model) {
        return userService
                .findById(id)
                .filter(u -> u.getAnonymizedAt() == null)
                .map(user -> {
                    model.addAttribute("user", user);
                    model.addAttribute("roles", UserRole.values());
                    model.addAttribute("rfidCards", userManagementService.listRfidCards(id));
                    return "settings-user-edit";
                })
                .orElse("redirect:/settings/users");
    }

    @PostMapping("/{id}")
    public String update(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam String username,
            @RequestParam String displayName,
            @RequestParam UserRole role,
            @RequestParam(required = false) String active,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        boolean isActive = "true".equalsIgnoreCase(active);
        try {
            userManagementService.updateUser(
                    id, username, displayName, role, isActive, actor.getUserId(), request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Benutzer wurde gespeichert.");
            return "redirect:/settings/users/" + id;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/users/" + id;
        }
    }

    @PostMapping("/{id}/password")
    public String resetPassword(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam String newPassword,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            userManagementService.setPasswordByAdmin(id, newPassword, actor.getUserId(), request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Passwort wurde gesetzt.");
            return "redirect:/settings/users/" + id;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/users/" + id;
        }
    }

    @PostMapping("/{id}/rfid")
    public String addRfid(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam String cardUid,
            @RequestParam(required = false) String label,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            userManagementService.registerRfidCard(id, cardUid, label, actor.getUserId(), request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "RFID-Chip wurde registriert.");
            return "redirect:/settings/users/" + id;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/users/" + id;
        }
    }

    @PostMapping("/{id}/rfid/{cardId}/revoke")
    public String revokeRfid(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @PathVariable long cardId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            userManagementService.revokeRfidCard(cardId, actor.getUserId(), request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "RFID-Chip wurde deaktiviert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings/users/" + id;
    }

    @PostMapping("/{id}/anonymize")
    public String anonymize(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        if (id == actor.getUserId()) {
            redirectAttributes.addFlashAttribute("error", "Sie können sich nicht selbst löschen.");
            return "redirect:/settings/users/" + id;
        }
        try {
            userService.anonymizeUser(id);
            auditService.record(AuditEventType.USER_ANONYMIZED, actor.getUserId(), id, request, null);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Benutzerkonto wurde gelöscht.");
            return "redirect:/settings/users";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/users/" + id;
        }
    }
}
