package de.feuerwehr.manager.web;

import de.feuerwehr.manager.berichte.EinsatzberichtService;
import de.feuerwehr.manager.divera.ManualAlarmDefaults;
import de.feuerwehr.manager.divera.ManualAlarmService;
import de.feuerwehr.manager.divera.ManualAlarmService.ActionResult;
import de.feuerwehr.manager.divera.ManualAlarmService.CreateResult;
import de.feuerwehr.manager.divera.ManualAlarmService.ManualAlarmInput;
import de.feuerwehr.manager.divera.ManualAlarmService.StartResult;
import de.feuerwehr.manager.divera.ManualAlarmService.UpdateResult;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/einsatz/manuell")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'UNIT_ADMIN')")
public class ManualAlarmController {

    private final UnitService unitService;
    private final AccessControlService accessControlService;
    private final ManualAlarmService manualAlarmService;
    private final EinsatzberichtService einsatzberichtService;

    @GetMapping
    public String form(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model) {
        Unit unit = resolveUnit(unitId, actor, model);
        ManualAlarmDefaults.FormDefaults defaults = ManualAlarmDefaults.forUnit(unit);
        model.addAttribute("pageTitle", "Einsatz anlegen");
        model.addAttribute("pageSubtitle", unit.getName());
        model.addAttribute("defaults", defaults);
        model.addAttribute("geraetehausAddress", defaults.geraetehausAddress());
        model.addAttribute("suggestedAlarmNumber", manualAlarmService.suggestAlarmNumber(unit.getId()));
        model.addAttribute("knownStichworte", einsatzberichtService.listKnownStichworte(unit.getId()));
        model.addAttribute("editMode", false);
        return "einsatz/manuell-form";
    }

    @GetMapping("/{id}/bearbeiten")
    public String editForm(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @PathVariable long id,
            Model model,
            RedirectAttributes redirectAttributes) {
        resolveUnit(unit, actor, model);
        try {
            var alarm = manualAlarmService.getOpenDraft(unit, id);
            model.addAttribute("editMode", true);
            model.addAttribute("draftId", id);
            model.addAttribute("alarm", alarm);
            model.addAttribute("pageTitle", "Einsatz bearbeiten");
            model.addAttribute("pageSubtitle", alarm.getTitle());
            model.addAttribute("defaults", ManualAlarmDefaults.forUnit(alarm.getUnit()));
            model.addAttribute("geraetehausAddress", ManualAlarmDefaults.forUnit(alarm.getUnit()).geraetehausAddress());
            model.addAttribute("knownStichworte", einsatzberichtService.listKnownStichworte(unit));
            return "einsatz/manuell-form";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/?unit=" + unit;
        }
    }

    @GetMapping("/suggest-number")
    @ResponseBody
    public ResponseEntity<String> suggestNumber(
            @AuthenticationPrincipal AppUserDetails actor, @RequestParam long unit) {
        accessControlService.requireUnitAccess(actor, unit);
        return ResponseEntity.ok(manualAlarmService.suggestAlarmNumber(unit));
    }

    @PostMapping
    public String create(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String title,
            @RequestParam(required = false) String alarmNumber,
            @RequestParam(required = false) String meldebild,
            @RequestParam(required = false) String bemerkung,
            @RequestParam(required = false) String street,
            @RequestParam(required = false) String houseNumber,
            @RequestParam(required = false) String postalCode,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String objectName,
            @RequestParam(required = false) String reporterName,
            @RequestParam(required = false) String reporterPhone,
            @RequestParam(required = false, defaultValue = "Notruf 112") String meldeweg,
            @RequestParam(required = false) String beteiligteEinsatzmittel,
            @RequestParam(required = false) String leitstelleName,
            @RequestParam(required = false) String leitstelleAddress,
            @RequestParam(required = false) String leitstellePhone,
            @RequestParam(required = false) String leitstelleEmail,
            @RequestParam(required = false, defaultValue = "false") boolean exercise,
            @RequestParam(required = false, defaultValue = "true") boolean sondersignal,
            @RequestParam(required = false, defaultValue = "false") boolean another,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireUnitAccess(actor, unit);
            ManualAlarmInput input = toInput(
                    title,
                    alarmNumber,
                    meldebild,
                    bemerkung,
                    street,
                    houseNumber,
                    postalCode,
                    city,
                    district,
                    objectName,
                    reporterName,
                    reporterPhone,
                    meldeweg,
                    beteiligteEinsatzmittel,
                    leitstelleName,
                    leitstelleAddress,
                    leitstellePhone,
                    leitstelleEmail,
                    exercise,
                    sondersignal);
            CreateResult result = manualAlarmService.createDraft(unit, actor.getUserId(), input);
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Einsatz „" + result.alarm().getTitle() + "“ gespeichert — auf der Startseite starten.");
            if (another) {
                return "redirect:/einsatz/manuell?unit=" + unit;
            }
            return "redirect:/?unit=" + unit;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/einsatz/manuell?unit=" + unit;
        }
    }

    @PostMapping("/{id}")
    public String update(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @PathVariable long id,
            @RequestParam String title,
            @RequestParam(required = false) String alarmNumber,
            @RequestParam(required = false) String meldebild,
            @RequestParam(required = false) String bemerkung,
            @RequestParam(required = false) String street,
            @RequestParam(required = false) String houseNumber,
            @RequestParam(required = false) String postalCode,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String objectName,
            @RequestParam(required = false) String reporterName,
            @RequestParam(required = false) String reporterPhone,
            @RequestParam(required = false, defaultValue = "Notruf 112") String meldeweg,
            @RequestParam(required = false) String beteiligteEinsatzmittel,
            @RequestParam(required = false) String leitstelleName,
            @RequestParam(required = false) String leitstelleAddress,
            @RequestParam(required = false) String leitstellePhone,
            @RequestParam(required = false) String leitstelleEmail,
            @RequestParam(required = false, defaultValue = "false") boolean exercise,
            @RequestParam(required = false, defaultValue = "true") boolean sondersignal,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireUnitAccess(actor, unit);
            ManualAlarmInput input = toInput(
                    title,
                    alarmNumber,
                    meldebild,
                    bemerkung,
                    street,
                    houseNumber,
                    postalCode,
                    city,
                    district,
                    objectName,
                    reporterName,
                    reporterPhone,
                    meldeweg,
                    beteiligteEinsatzmittel,
                    leitstelleName,
                    leitstelleAddress,
                    leitstellePhone,
                    leitstelleEmail,
                    exercise,
                    sondersignal);
            UpdateResult result = manualAlarmService.updateDraft(unit, id, input);
            redirectAttributes.addFlashAttribute(
                    "success", "Einsatz „" + result.alarm().getTitle() + "“ gespeichert.");
            return "redirect:/?unit=" + unit;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/einsatz/manuell/" + id + "/bearbeiten?unit=" + unit;
        }
    }

    @PostMapping("/{id}/start")
    public String start(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @PathVariable long id,
            @RequestParam(required = false, defaultValue = "true") boolean useGeraetehaus,
            @RequestParam(required = false) String routeStartAddress,
            @RequestParam(required = false, defaultValue = "true") boolean computeRoute,
            @RequestParam(required = false, defaultValue = "true") boolean sendPush,
            @RequestParam(required = false, defaultValue = "false") boolean printDepesche,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireUnitAccess(actor, unit);
            StartResult result = manualAlarmService.startAlarm(
                    unit, id, useGeraetehaus, routeStartAddress, computeRoute, sendPush, printDepesche);
            StringBuilder msg = new StringBuilder("Einsatz gestartet.");
            if (result.routeMessage() != null) {
                msg.append(' ').append(result.routeMessage());
            }
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
            return "redirect:/?unit=" + unit;
        }
    }

    @PostMapping("/{id}/depesche")
    public String printDepesche(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @PathVariable long id,
            @RequestParam(required = false, defaultValue = "true") boolean useGeraetehaus,
            @RequestParam(required = false) String routeStartAddress,
            @RequestParam(required = false, defaultValue = "true") boolean computeRoute,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireUnitAccess(actor, unit);
            ActionResult result =
                    manualAlarmService.printDepesche(unit, id, useGeraetehaus, routeStartAddress, computeRoute);
            redirectAttributes.addFlashAttribute("success", result.message());
            return "redirect:/?unit=" + unit;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/?unit=" + unit;
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @PathVariable long id,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireUnitAccess(actor, unit);
            manualAlarmService.deleteDraft(unit, id);
            redirectAttributes.addFlashAttribute("success", "Geplanter Einsatz gelöscht.");
            return "redirect:/?unit=" + unit;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/?unit=" + unit;
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
            redirectAttributes.addFlashAttribute("success", "Einsatz beendet.");
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

    private static ManualAlarmInput toInput(
            String title,
            String alarmNumber,
            String meldebild,
            String bemerkung,
            String street,
            String houseNumber,
            String postalCode,
            String city,
            String district,
            String objectName,
            String reporterName,
            String reporterPhone,
            String meldeweg,
            String beteiligteEinsatzmittel,
            String leitstelleName,
            String leitstelleAddress,
            String leitstellePhone,
            String leitstelleEmail,
            boolean exercise,
            boolean sondersignal) {
        return new ManualAlarmInput(
                alarmNumber,
                title,
                meldebild,
                bemerkung,
                street,
                houseNumber,
                postalCode,
                city,
                district,
                objectName,
                reporterName,
                reporterPhone,
                meldeweg,
                beteiligteEinsatzmittel,
                leitstelleName,
                leitstelleAddress,
                leitstellePhone,
                leitstelleEmail,
                exercise,
                sondersignal);
    }
}
