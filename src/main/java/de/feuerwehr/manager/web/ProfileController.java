package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.AppUserDetailsService;
import de.feuerwehr.manager.security.SecurityProperties;
import de.feuerwehr.manager.user.UserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserManagementService userManagementService;
    private final AppUserDetailsService appUserDetailsService;
    private final SecurityProperties securityProperties;

    @GetMapping("/password")
    public String passwordForm() {
        return "redirect:/settings";
    }

    @GetMapping("/password-required")
    public String passwordRequired(@AuthenticationPrincipal AppUserDetails user, Model model) {
        if (user == null || !user.isMustChangePassword()) {
            return "redirect:/";
        }
        model.addAttribute("minPasswordLength", securityProperties.minPasswordLength());
        model.addAttribute("forcedPasswordChange", true);
        return "profile-password-required";
    }

    @PostMapping("/password")
    public String changePassword(
            @AuthenticationPrincipal AppUserDetails user,
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String newPasswordConfirm,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        boolean forced = user != null && user.isMustChangePassword();
        if (!newPassword.equals(newPasswordConfirm)) {
            redirectAttributes.addFlashAttribute("error", "Neue Passwörter stimmen nicht überein.");
            return forced ? "redirect:/profile/password-required" : "redirect:/settings";
        }
        try {
            userManagementService.changeOwnPassword(user.getUserId(), currentPassword, newPassword);
            refreshPrincipal(user.getUsername(), request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Passwort wurde geändert.");
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return forced ? "redirect:/profile/password-required" : "redirect:/settings";
        }
    }

    private void refreshPrincipal(String username, HttpServletRequest request) {
        AppUserDetails details = (AppUserDetails) appUserDetailsService.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext());
        }
    }
}
