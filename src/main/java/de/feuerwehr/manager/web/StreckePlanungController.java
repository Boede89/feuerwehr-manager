package de.feuerwehr.manager.web;

import de.feuerwehr.manager.atemschutz.StreckePlanungNotificationService;
import de.feuerwehr.manager.atemschutz.StreckePlanungNotificationService.AusbilderOption;
import de.feuerwehr.manager.atemschutz.StreckePlanungNotificationService.NotifyResult;
import de.feuerwehr.manager.atemschutz.StreckePlanungService;
import de.feuerwehr.manager.atemschutz.StreckePlanungService.AutoAssignResult;
import de.feuerwehr.manager.atemschutz.StreckePlanungService.CreateTerminRequest;
import de.feuerwehr.manager.atemschutz.StreckePlanungService.StreckePlanungView;
import de.feuerwehr.manager.atemschutz.StreckePlanungService.UpdateTerminRequest;
import de.feuerwehr.manager.mail.UnitMailService;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.web.dto.ActionResultDto;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/atemschutz/strecke-planung")
@RequiredArgsConstructor
public class StreckePlanungController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final UserPermissionService userPermissionService;
    private final StreckePlanungService streckePlanungService;
    private final StreckePlanungNotificationService notificationService;
    private final UnitMailService unitMailService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireAtemschutzRead(actor, unit.getId());
            boolean includeHealth = actor.getRole().isAdminLevel();
            StreckePlanungView view = streckePlanungService.loadView(unit.getId(), includeHealth);
            model.addAttribute("unassignedCarriers", view.unassignedCarriers());
            model.addAttribute("termine", view.termine());
            model.addAttribute("warnDays", view.warnDays());
            model.addAttribute("canWrite", canWrite(actor, unit.getId()));
            model.addAttribute("canMail", unitMailService.canSendForUnit(unit.getId()));
            return "atemschutz/strecke-planung";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId != null ? "redirect:/atemschutz?unit=" + unitId : "redirect:/";
        } catch (RuntimeException e) {
            log.error("Strecke-Terminplanung laden fehlgeschlagen (unit={})", unitId, e);
            redirectAttributes.addFlashAttribute(
                    "error", "Strecke-Terminplanung konnte nicht geladen werden: " + e.getMessage());
            return unitId != null ? "redirect:/atemschutz?unit=" + unitId : "redirect:/";
        }
    }

    @PostMapping("/api/termine")
    @ResponseBody
    public ActionResultDto createTermine(
            @AuthenticationPrincipal AppUserDetails actor, @RequestParam long unit, @RequestBody CreateTermineBody body) {
        try {
            requireModuleEnabled(unit);
            requireAtemschutzWrite(actor, unit);
            accessControlService.requireUnitAccess(actor, unit);
            if (body == null || body.termine() == null || body.termine().isEmpty()) {
                throw new IllegalArgumentException("Bitte mindestens einen Termin angeben.");
            }
            List<CreateTerminRequest> requests = body.termine().stream()
                    .map(CreateTerminBody::toRequest)
                    .toList();
            int count = streckePlanungService.createTermine(unit, actor.getUserId(), requests);
            String message = count == 1 ? "1 Termin erstellt." : count + " Termine erstellt.";
            return ActionResultDto.success(message);
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @PutMapping("/api/termine/{id}")
    @ResponseBody
    public ActionResultDto updateTermin(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @PathVariable long id,
            @RequestBody UpdateTerminBody body) {
        try {
            requireModuleEnabled(unit);
            requireAtemschutzWrite(actor, unit);
            accessControlService.requireUnitAccess(actor, unit);
            streckePlanungService.updateTermin(
                    unit,
                    id,
                    new UpdateTerminRequest(
                            body.terminDatum(), body.terminZeit(), body.ort(), body.maxTeilnehmer(), body.bemerkung()));
            return ActionResultDto.success("Termin aktualisiert.");
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @DeleteMapping("/api/termine/{id}")
    @ResponseBody
    public ActionResultDto deleteTermin(
            @AuthenticationPrincipal AppUserDetails actor, @RequestParam long unit, @PathVariable long id) {
        try {
            requireModuleEnabled(unit);
            requireAtemschutzWrite(actor, unit);
            accessControlService.requireUnitAccess(actor, unit);
            streckePlanungService.deleteTermin(unit, id);
            return ActionResultDto.success("Termin gelöscht.");
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @GetMapping("/drucken")
    public String drucken(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireAtemschutzRead(actor, unit.getId());
            StreckePlanungView view = streckePlanungService.loadView(unit.getId(), false);
            model.addAttribute("unassignedCarriers", view.unassignedCarriers());
            model.addAttribute("termine", view.termine());
            model.addAttribute("warnDays", view.warnDays());
            return "atemschutz/strecke-planung-druck";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId != null ? "redirect:/atemschutz/strecke-planung?unit=" + unitId : "redirect:/";
        }
    }

    @GetMapping("/api/ausbilder")
    @ResponseBody
    public List<AusbilderOption> listAusbilder(
            @AuthenticationPrincipal AppUserDetails actor, @RequestParam long unit) {
        requireModuleEnabled(unit);
        requireAtemschutzWrite(actor, unit);
        accessControlService.requireUnitAccess(actor, unit);
        return notificationService.listAusbilder(unit);
    }

    @PostMapping("/api/email")
    @ResponseBody
    public ActionResultDto sendEmail(
            @AuthenticationPrincipal AppUserDetails actor, @RequestParam long unit, @RequestBody EmailBody body) {
        try {
            requireModuleEnabled(unit);
            requireAtemschutzWrite(actor, unit);
            accessControlService.requireUnitAccess(actor, unit);
            if (!unitMailService.canSendForUnit(unit)) {
                throw new IllegalArgumentException(
                        "SMTP der Einheit ist nicht konfiguriert (Admin → Einheit → Schnittstellen).");
            }
            if (body == null || body.action() == null) {
                throw new IllegalArgumentException("Unbekannte Aktion.");
            }
            NotifyResult result =
                    switch (body.action()) {
                        case "einzeln_informieren" -> notificationService.notifyCarrier(
                                unit, body.terminId(), body.carrierId());
                        case "termin_informieren" -> notificationService.notifyTermin(unit, body.terminId());
                        case "alle_informieren" -> notificationService.notifyAll(unit);
                        case "ausbilder_informieren" -> notificationService.notifyAusbilder(unit, body.ausbilderIds());
                        default -> throw new IllegalArgumentException("Unbekannte Aktion.");
                    };
            return result.ok() ? ActionResultDto.success(result.message()) : ActionResultDto.failure(result.message());
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @PostMapping("/api/zuordnung")
    @ResponseBody
    public ActionResultDto zuordnung(
            @AuthenticationPrincipal AppUserDetails actor, @RequestParam long unit, @RequestBody ZuordnungBody body) {
        try {
            requireModuleEnabled(unit);
            requireAtemschutzWrite(actor, unit);
            accessControlService.requireUnitAccess(actor, unit);
            return switch (body.action()) {
                case "zuordnen" -> {
                    streckePlanungService.assignCarrier(unit, body.terminId(), body.carrierId());
                    yield ActionResultDto.success("Zuordnung gespeichert.");
                }
                case "entfernen" -> {
                    streckePlanungService.removeAssignment(unit, body.terminId(), body.carrierId());
                    yield ActionResultDto.success("Zuordnung entfernt.");
                }
                case "zurueck_in_pool" -> {
                    streckePlanungService.removeCarrierFromAnyAssignment(unit, body.carrierId());
                    yield ActionResultDto.success("Geräteträger zurück in den Pool verschoben.");
                }
                case "auto_zuordnung" -> {
                    AutoAssignResult result = streckePlanungService.autoAssign(unit);
                    yield ActionResultDto.success(result.message());
                }
                case "alle_loeschen" -> {
                    int count = streckePlanungService.clearAllAssignments(unit);
                    yield ActionResultDto.success(count + " Zuordnung(en) wurden gelöscht.");
                }
                default -> ActionResultDto.failure("Unbekannte Aktion.");
            };
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
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

    private void requireModuleEnabled(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.ATEMSCHUTZ, unitId)) {
            throw new IllegalArgumentException("Das Modul Atemschutz ist für diese Einheit nicht aktiviert.");
        }
    }

    private void requireAtemschutzRead(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "atemschutz.read");
    }

    private void requireAtemschutzWrite(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "atemschutz.write");
    }

    private boolean canWrite(AppUserDetails actor, long unitId) {
        return userPermissionService.hasPermission(actor, unitId, "atemschutz.write");
    }

    public record CreateTermineBody(List<CreateTerminBody> termine) {}

    public record CreateTerminBody(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate terminDatum,
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime terminZeit,
            String ort,
            int maxTeilnehmer,
            String bemerkung) {
        CreateTerminRequest toRequest() {
            return new CreateTerminRequest(terminDatum, terminZeit, ort, maxTeilnehmer, bemerkung);
        }
    }

    public record UpdateTerminBody(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate terminDatum,
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime terminZeit,
            String ort,
            int maxTeilnehmer,
            String bemerkung) {}

    public record ZuordnungBody(String action, Long terminId, Long carrierId) {}

    public record EmailBody(String action, Long terminId, Long carrierId, List<Long> ausbilderIds) {}
}
