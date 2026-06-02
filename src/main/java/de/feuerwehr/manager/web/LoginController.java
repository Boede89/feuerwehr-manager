package de.feuerwehr.manager.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String expired,
            Model model) {
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
