package de.feuerwehr.manager.web;

import de.feuerwehr.manager.dsgvo.AuditEventType;
import de.feuerwehr.manager.dsgvo.AuditService;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserManagementService;
import de.feuerwehr.manager.user.UserRole;
import de.feuerwehr.manager.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'UNIT_ADMIN')")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserManagementService userManagementService;
    private final UserService userService;
    private final UnitService unitService;
    private final AuditService auditService;
    private final AccessControlService accessControlService;

    @GetMapping
    public String list(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitParam,
            Model model) {
        Long scopeUnitId = resolveScopeUnitId(actor, unitParam);
        populateUserFormModel(actor, model, scopeUnitId);
        model.addAttribute("users", userManagementService.listAccounts(actor, scopeUnitId));
        model.addAttribute("scopeUnitId", scopeUnitId);
        return "settings-users";
    }

    @PostMapping
    public String create(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam String username,
            @RequestParam String displayName,
            @RequestParam String password,
            @RequestParam(defaultValue = "USER") UserRole role,
            @RequestParam(required = false) Long unitId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            User created = userManagementService.createUser(
                    username, displayName, null, password, role, unitId, null, null, actor, request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute(
                    "message", "Benutzer „" + created.getUsername() + "“ wurde angelegt.");
            Long redirectUnit = unitId;
            if (redirectUnit == null && created.getUnit() != null) {
                redirectUnit = created.getUnit().getId();
            }
            return redirectUsersList(actor, redirectUnit);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectUsersList(actor, unitId);
        }
    }

    @GetMapping("/{id}")
    public String editForm(@AuthenticationPrincipal AppUserDetails actor, @PathVariable long id, Model model) {
        return userService
                .findByIdWithUnit(id)
                .filter(u -> u.getAnonymizedAt() == null)
                .map(user -> {
                    try {
                        accessControlService.requireCanManageUser(actor, user);
                    } catch (IllegalArgumentException e) {
                        return "redirect:/settings/users";
                    }
                    model.addAttribute("user", user);
                    populateUserFormModel(actor, model);
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
            @RequestParam(required = false) String loginEmail,
            @RequestParam UserRole role,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) String active,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        boolean isActive = "true".equalsIgnoreCase(active);
        try {
            userManagementService.updateUser(
                    id, username, displayName, loginEmail, role, unitId, isActive, actor, request);
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
            userManagementService.setPasswordByAdmin(id, newPassword, actor, request);
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
            userManagementService.registerRfidCard(id, cardUid, label, actor, request);
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
            userManagementService.revokeRfidCard(cardId, actor, request);
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
            User target = userService.findByIdWithUnit(id).orElseThrow();
            accessControlService.requireCanManageUser(actor, target);
            String auditDetail = target.getUsername() + " · " + target.getDisplayName();
            userService.anonymizeUser(id);
            auditService.record(AuditEventType.USER_ANONYMIZED, actor.getUserId(), id, request, auditDetail);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Benutzerkonto wurde gelöscht.");
            return "redirect:/settings/users";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/users/" + id;
        }
    }

    private void populateUserFormModel(AppUserDetails actor, Model model) {
        populateUserFormModel(actor, model, null);
    }

    private void populateUserFormModel(AppUserDetails actor, Model model, Long scopeUnitId) {
        List<UserRole> roles = UserRole.assignableBy(actor.getRole()).stream().sorted().toList();
        model.addAttribute("roles", roles);
        model.addAttribute("units", unitService.findActiveOrdered(actor));
        model.addAttribute("isSuperAdmin", actor.getRole().isSuperAdmin());
        model.addAttribute("formUnitId", scopeUnitId);
    }

    private Long resolveScopeUnitId(AppUserDetails actor, Long unitParam) {
        if (actor == null || !actor.getRole().isSuperAdmin() || unitParam == null) {
            return null;
        }
        return unitService.resolveActiveUnit(unitParam, actor).map(u -> u.getId()).orElse(null);
    }

    private static String redirectUsersList(AppUserDetails actor, Long unitId) {
        if (actor != null && actor.getRole().isSuperAdmin() && unitId != null && unitId > 0) {
            return "redirect:/settings/users?unit=" + unitId;
        }
        return "redirect:/settings/users";
    }
}
