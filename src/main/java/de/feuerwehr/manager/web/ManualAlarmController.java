package de.feuerwehr.manager.web;

import de.feuerwehr.manager.divera.ManualAlarmService;
import de.feuerwehr.manager.divera.ManualAlarmService.CreateResult;
import de.feuerwehr.manager.divera.ManualAlarmService.ManualAlarmInput;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
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
@RequestMapping("/einsatz/manuell")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'UNIT_ADMIN')")
public class ManualAlarmController {

    private final UnitService unitService;
    private final AccessControlService accessControlService;
    private final ManualAlarmService manualAlarmService;

    @GetMapping
    public String form(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model) {
        Unit unit = resolveUnit(unitId, actor, model);
        model.addAttribute("pageTitle", "Einsatz manuell anlegen");
        model.addAttribute("pageSubtitle", unit.getName());
        return "einsatz/manuell-form";
    }

    @PostMapping
    public String create(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String title,
            @RequestParam(required = false) String alarmNumber,
            @RequestParam(required = false) String incidentCategory,
            @RequestParam(required = false) String alarmText,
            @RequestParam(required = false) String meldebildZusatz,
            @RequestParam(required = false) String street,
            @RequestParam(required = false) String houseNumber,
            @RequestParam(required = false) String postalCode,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String objectName,
            @RequestParam(required = false) String reporterName,
            @RequestParam(required = false) String reporterPhone,
            @RequestParam(required = false) String callbackPhone,
            @RequestParam(required = false, defaultValue = "Notruf 112") String meldeweg,
            @RequestParam(required = false) String beteiligteEinsatzmittel,
            @RequestParam(required = false) String routeInfo,
            @RequestParam(required = false) String leitstelleName,
            @RequestParam(required = false) String leitstelleAddress,
            @RequestParam(required = false) String leitstellePhone,
            @RequestParam(required = false) String leitstelleEmail,
            @RequestParam(required = false, defaultValue = "false") boolean sendPush,
            @RequestParam(required = false, defaultValue = "false") boolean printDepesche,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireUnitAccess(actor, unit);
            ManualAlarmInput input = new ManualAlarmInput(
                    alarmNumber,
                    incidentCategory,
                    title,
                    alarmText,
                    meldebildZusatz,
                    street,
                    houseNumber,
                    postalCode,
                    city,
                    district,
                    objectName,
                    reporterName,
                    reporterPhone,
                    callbackPhone,
                    meldeweg,
                    beteiligteEinsatzmittel,
                    routeInfo,
                    leitstelleName,
                    leitstelleAddress,
                    leitstellePhone,
                    leitstelleEmail);
            CreateResult result = manualAlarmService.create(unit, actor.getUserId(), input, sendPush, printDepesche);
            StringBuilder msg = new StringBuilder("Einsatz angelegt — sichtbar auf der Startseite.");
            if (result.pushMessage() != null) {
                msg.append(' ').append(result.pushMessage());
            }
            if (result.printMessage() != null) {
                msg.append(' ').append(result.printMessage());
            }
            redirectAttributes.addFlashAttribute("success", msg.toString());
            return "redirect:/?unit=" + unit;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/einsatz/manuell?unit=" + unit;
        }
    }

    @PostMapping("/{id}/close")
    public String close(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @PathVariable long id,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireUnitAccess(actor, unit);
            manualAlarmService.closeAlarm(unit, id);
            redirectAttributes.addFlashAttribute("success", "Manueller Einsatz beendet.");
            return "redirect:/?unit=" + unit;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/?unit=" + unit;
        }
    }

    private Unit resolveUnit(Long unitId, AppUserDetails actor, Model model) {
        Unit unit = unitService
                .resolveActiveUnit(unitId, actor)
                .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
        accessControlService.requireUnitAccess(actor, unit.getId());
        model.addAttribute("unitId", unit.getId());
        model.addAttribute("currentUnitName", unit.getName());
        return unit;
    }
}
