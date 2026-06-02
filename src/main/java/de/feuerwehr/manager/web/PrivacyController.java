package de.feuerwehr.manager.web;

import de.feuerwehr.manager.dsgvo.PrivacyNotice;
import de.feuerwehr.manager.dsgvo.PrivacyService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class PrivacyController {

    private final PrivacyService privacyService;
    private final UserService userService;

    @GetMapping("/privacy/notice")
    public String notice(Model model) {
        privacyService.getCurrentNotice().ifPresent(n -> model.addAttribute("notice", n));
        return "privacy-notice";
    }

    @GetMapping("/privacy/accept")
    public String acceptForm(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        PrivacyNotice notice = privacyService.getCurrentNotice()
                .orElseThrow(() -> new IllegalStateException("Kein Datenschutzhinweis konfiguriert"));
        model.addAttribute("notice", notice);
        model.addAttribute("displayName", principal.getDisplayName());
        return "privacy-accept";
    }

    @PostMapping("/privacy/accept")
    public String accept(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestParam(defaultValue = "false") boolean consent,
            HttpServletRequest request) {
        if (!consent) {
            return "redirect:/privacy/accept?required=1";
        }
        User user = userService.findById(principal.getUserId()).orElseThrow();
        privacyService.recordConsent(user, request, request.getHeader("User-Agent"));
        return "redirect:/";
    }
}
