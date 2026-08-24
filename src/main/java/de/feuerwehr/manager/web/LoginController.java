package de.feuerwehr.manager.web;

import de.feuerwehr.manager.reservierungen.ReservierungenSettingsService;
import de.feuerwehr.manager.security.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@Slf4j
public class LoginController {

    private final SecurityProperties securityProperties;
    private final ReservierungenSettingsService reservierungenSettingsService;

    @GetMapping("/login")
    public String login(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String expired,
            @RequestParam(required = false) String login,
            Model model) {
        model.addAttribute("rfidLoginEnabled", securityProperties.rfidApiEnabled());
        boolean publicReservationAvailable = false;
        try {
            publicReservationAvailable = !reservierungenSettingsService.listUnitsAllowingPublicReservation().isEmpty();
        } catch (Exception e) {
            log.warn("Öffentliche Reservierung konnte auf der Anmeldeseite nicht geprüft werden: {}", e.getMessage());
        }
        model.addAttribute("publicReservationAvailable", publicReservationAvailable);
        if (error != null) {
            model.addAttribute("errorMessage", "Anmeldung fehlgeschlagen. Bitte Zugangsdaten prüfen.");
        }
        if (logout != null) {
            model.addAttribute("infoMessage", "Sie wurden abgemeldet.");
        }
        if (expired != null) {
            model.addAttribute("infoMessage", "Ihre Sitzung ist abgelaufen. Bitte erneut anmelden.");
        }
        // Login-Overlay nur bei Fehler, abgelaufener Sitzung oder explizitem ?login=1 öffnen.
        boolean openLoginForm = error != null || expired != null || login != null;
        model.addAttribute("openLoginForm", openLoginForm);
        return "login";
    }
}
