package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class LoginController {

    private final SecurityProperties securityProperties;

    @GetMapping("/login")
    public String login(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String expired,
            Model model) {
        model.addAttribute("rfidLoginEnabled", securityProperties.rfidApiEnabled());
        if (error != null) {
            model.addAttribute("errorMessage", "Anmeldung fehlgeschlagen. Bitte Zugangsdaten prüfen.");
        }
        if (logout != null) {
            model.addAttribute("infoMessage", "Sie wurden abgemeldet.");
        }
        if (expired != null) {
            model.addAttribute("infoMessage", "Ihre Sitzung ist abgelaufen. Bitte erneut anmelden.");
        }
        return "login";
    }
}
