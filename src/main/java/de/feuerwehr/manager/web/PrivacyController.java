package de.feuerwehr.manager.web;

import de.feuerwehr.manager.dsgvo.PrivacyNotice;
import de.feuerwehr.manager.dsgvo.PrivacyService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.ApplicationSettings;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Stream;
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

    private static final DateTimeFormatter STAND_DATE =
            DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN);

    private final PrivacyService privacyService;
    private final UserService userService;
    private final GlobalSettingsService globalSettingsService;

    @GetMapping("/datenschutz")
    public String datenschutz(Model model) {
        populateDatenschutz(model);
        return "datenschutz";
    }

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

    private void populateDatenschutz(Model model) {
        ApplicationSettings s = globalSettingsService.get();
        model.addAttribute("orgName", orPlaceholder(s.getFfName(), "(Name der Feuerwehr)"));
        model.addAttribute(
                "orgAddress",
                Stream.of(s.getFfStrasse(), s.getFfOrt())
                        .filter(v -> v != null && !v.isBlank())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(null));
        model.addAttribute("kontaktName", orPlaceholder(s.getPrivacyContactName(), "(Verantwortliche Person)"));
        model.addAttribute("kontaktEmail", orPlaceholder(s.getPrivacyContactEmail(), "(E-Mail)"));
        model.addAttribute("kontaktTel", blankToNull(s.getPrivacyContactPhone()));
        model.addAttribute("hoster", orPlaceholder(s.getPrivacyHoster(), "Eigener Server"));
        model.addAttribute("standDate", LocalDate.now().format(STAND_DATE));
    }

    private static String orPlaceholder(String value, String placeholder) {
        return value != null && !value.isBlank() ? value.trim() : placeholder;
    }

    private static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
