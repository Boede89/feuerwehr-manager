package de.feuerwehr.manager.web;

import de.feuerwehr.manager.reservierungen.GoogleCalendarOAuthService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.unit.UnitService;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/unit/calendar/oauth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'UNIT_ADMIN')")
@RequiredArgsConstructor
public class GoogleCalendarOAuthController {

    private static final String SESSION_PREFIX = "googleCalendarOAuth:";

    private final UnitService unitService;
    private final GoogleCalendarOAuthService googleCalendarOAuthService;

    public record OAuthPending(long unitId, long calendarAccountId) {}

    @GetMapping("/connect")
    public String connect(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long calendarAccountId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            unitService
                    .resolveActiveUnit(unit, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
            String state = UUID.randomUUID().toString();
            session.setAttribute(SESSION_PREFIX + state, new OAuthPending(unit, calendarAccountId));
            return "redirect:" + googleCalendarOAuthService.buildAuthorizationUrl(state);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectSchnittstellen(unit);
        }
    }

    @GetMapping("/callback")
    public String callback(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        long unitId = 0;
        try {
            if (error != null && !error.isBlank()) {
                throw new IllegalArgumentException("Google OAuth abgebrochen: " + error);
            }
            if (code == null || code.isBlank() || state == null || state.isBlank()) {
                throw new IllegalArgumentException("Google OAuth: unvollständige Rückmeldung.");
            }
            Object pendingObj = session.getAttribute(SESSION_PREFIX + state);
            session.removeAttribute(SESSION_PREFIX + state);
            if (!(pendingObj instanceof OAuthPending pending)) {
                throw new IllegalArgumentException("Google OAuth: Sitzung abgelaufen – bitte erneut verbinden.");
            }
            unitId = pending.unitId();
            unitService
                    .resolveActiveUnit(pending.unitId(), actor)
                    .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
            var result = googleCalendarOAuthService.completeAuthorization(
                    pending.unitId(), pending.calendarAccountId(), code);
            String email = result.userEmail() != null ? result.userEmail() : "Google-Konto";
            redirectAttributes.addFlashAttribute(
                    "message", "Google Kalender verbunden als " + email + ". Bitte „Test“ ausführen.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Google-Verbindung fehlgeschlagen: " + e.getMessage());
        }
        return redirectSchnittstellen(unitId);
    }

    private static String redirectSchnittstellen(long unitId) {
        if (unitId > 0) {
            return "redirect:/admin?scope=einheit&tab=schnittstellen&unit=" + unitId + "#calendar-google";
        }
        return "redirect:/admin?scope=einheit&tab=schnittstellen#calendar-google";
    }
}
