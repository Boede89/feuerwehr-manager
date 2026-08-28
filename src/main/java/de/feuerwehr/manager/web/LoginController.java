package de.feuerwehr.manager.web;

import de.feuerwehr.manager.berichte.AttendanceCheckInService;
import de.feuerwehr.manager.reservierungen.ReservierungenSettingsService;
import de.feuerwehr.manager.security.SecurityProperties;
import java.util.List;
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
    private final AttendanceCheckInService attendanceCheckInService;

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
        try {
            var publicCheckInOptions = attendanceCheckInService.listPublicCheckInOptions();
            model.addAttribute("publicCheckInOptions", publicCheckInOptions);
            model.addAttribute("showCheckInUnitNames", publicCheckInOptions.size() > 1);
        } catch (Exception e) {
            log.warn("Öffentliche Check-In-Termine konnten nicht geladen werden: {}", e.getMessage(), e);
            model.addAttribute("publicCheckInOptions", List.of());
            model.addAttribute("showCheckInUnitNames", false);
        }
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
