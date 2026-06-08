package de.feuerwehr.manager.web;

import de.feuerwehr.manager.berichte.BerichteTab;
import de.feuerwehr.manager.berichte.CrewAssignment;
import de.feuerwehr.manager.berichte.EinsatzberichtForm;
import de.feuerwehr.manager.berichte.EinsatzberichtService;
import de.feuerwehr.manager.berichte.IncidentReport;
import de.feuerwehr.manager.divera.DiveraEinsatzberichtSyncService;
import de.feuerwehr.manager.berichte.KraefteFahrzeugeState;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/berichte")
@RequiredArgsConstructor
public class BerichteController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final UserPermissionService userPermissionService;
    private final EinsatzberichtService einsatzberichtService;
    private final DiveraEinsatzberichtSyncService diveraEinsatzberichtSyncService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "tab", defaultValue = "einsatz") String tab,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireBerichteRead(actor, unit.getId());
            BerichteTab berichteTab = BerichteTab.fromKey(tab);
            model.addAttribute("berichteTab", berichteTab.key());
            model.addAttribute("berichteTabs", BerichteTab.values());
            model.addAttribute("canWrite", canWrite(actor, unit.getId()));
            if (berichteTab == BerichteTab.EINSATZ) {
                DiveraEinsatzberichtSyncService.SyncResult sync =
                        diveraEinsatzberichtSyncService.syncAlarmsForUnit(unit.getId());
                if (sync.success() && sync.created() > 0) {
                    model.addAttribute(
                            "message",
                            sync.created() + " Einsatzbericht/Einsatzberichte aus DIVERA als Entwurf übernommen.");
                }
                model.addAttribute("einsatzberichte", einsatzberichtService.listByUnit(unit.getId()));
            }
            return "berichte/index";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectHome(unitId);
        }
    }

    @GetMapping("/einsatzberichte/neu")
    public String newEinsatzbericht(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireBerichteWrite(actor, unit.getId());
            populateEinsatzFormModel(model, unit.getId(), null, einsatzberichtService.newForm(unit.getId()), false);
            model.addAttribute("formMode", "create");
            model.addAttribute("pageTitle", "Neuer Einsatzbericht");
            model.addAttribute("pageSubtitle", "Entwurf — wird nach dem Speichern zur Freigabe vorgelegt");
            return "berichte/einsatzbericht-form";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, "einsatz");
        }
    }

    @GetMapping("/einsatzberichte/{id}")
    public String viewEinsatzbericht(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "returnUrl", required = false) String returnUrl,
            @PathVariable long id,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireBerichteRead(actor, unit.getId());
            IncidentReport report = einsatzberichtService.requireReport(unit.getId(), id);
            EinsatzberichtForm form = EinsatzberichtForm.fromReport(report);
            populateEinsatzFormModel(model, unit.getId(), report, form, false);
            model.addAttribute("formMode", "view");
            model.addAttribute("canWrite", canWrite(actor, unit.getId()));
            model.addAttribute("backUrl", buildBackUrl(sanitizeReturnUrl(returnUrl), unit.getId()));
            model.addAttribute("pageTitle", "Einsatzbericht");
            model.addAttribute(
                    "pageSubtitle",
                    report.getIncidentNumber() != null ? report.getIncidentNumber() : "Anzeige");
            return "berichte/einsatzbericht-view";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            String safeReturn = sanitizeReturnUrl(returnUrl);
            if (safeReturn != null) {
                return "redirect:" + safeReturn;
            }
            return redirectBerichte(unitId, "einsatz");
        }
    }

    @GetMapping("/einsatzberichte/{id}/bearbeiten")
    public String editEinsatzbericht(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @PathVariable long id,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireBerichteWrite(actor, unit.getId());
            IncidentReport report = einsatzberichtService.requireReport(unit.getId(), id);
            EinsatzberichtForm form = EinsatzberichtForm.fromReport(report);
            populateEinsatzFormModel(model, unit.getId(), report, form, true);
            model.addAttribute("formMode", "edit");
            model.addAttribute("pageTitle", "Einsatzbericht bearbeiten");
            model.addAttribute("pageSubtitle", report.getIncidentNumber() != null ? report.getIncidentNumber() : "Entwurf");
            return "berichte/einsatzbericht-form";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, "einsatz");
        }
    }

    @PostMapping("/einsatzberichte")
    public String createEinsatzbericht(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @ModelAttribute EinsatzberichtForm form,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor);
            requireModuleEnabled(unit.getId());
            requireBerichteWrite(actor, unit.getId());
            List<CrewAssignment> crewAssignments = einsatzberichtService.parseCrewAssignments(form.getCrewAssignmentsJson());
            einsatzberichtService.create(unit.getId(), form.toData(crewAssignments), actor);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Einsatzbericht wurde gespeichert.");
            return redirectBerichte(unit.getId(), "einsatz");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, "einsatz");
        }
    }

    @PostMapping("/einsatzberichte/{id}")
    public String updateEinsatzbericht(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @PathVariable long id,
            @ModelAttribute EinsatzberichtForm form,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor);
            requireModuleEnabled(unit.getId());
            requireBerichteWrite(actor, unit.getId());
            List<CrewAssignment> crewAssignments = einsatzberichtService.parseCrewAssignments(form.getCrewAssignmentsJson());
            einsatzberichtService.update(unit.getId(), id, form.toData(crewAssignments), actor);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Einsatzbericht wurde aktualisiert.");
            return redirectBerichte(unit.getId(), "einsatz");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, "einsatz");
        }
    }

    @PostMapping("/einsatzberichte/{id}/delete")
    public String deleteEinsatzbericht(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @PathVariable long id,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor);
            requireModuleEnabled(unit.getId());
            requireBerichteWrite(actor, unit.getId());
            einsatzberichtService.delete(unit.getId(), id);
            redirectAttributes.addFlashAttribute("message", "Einsatzbericht wurde gelöscht.");
            return redirectBerichte(unit.getId(), "einsatz");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, "einsatz");
        }
    }

    @PostMapping("/platzhalter")
    public String placeholderCreate(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "tab") String tab,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor);
            requireModuleEnabled(unit.getId());
            requireBerichteWrite(actor, unit.getId());
            redirectAttributes.addFlashAttribute("error", "Dieser Berichtstyp wird als Nächstes umgesetzt.");
            return redirectBerichte(unit.getId(), tab);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, tab);
        }
    }

    private void populateEinsatzFormModel(
            Model model, long unitId, IncidentReport report, EinsatzberichtForm form, boolean refreshDivera) {
        Long reportId = report != null ? report.getId() : null;
        if (refreshDivera && reportId != null && report.getDiveraAlarmId() != null) {
            einsatzberichtService.refreshDiveraFromLatestAlarmData(unitId, reportId);
            report = einsatzberichtService.requireReport(unitId, reportId);
            form.setIncidentDate(report.getIncidentDate());
            form.setAlarmTime(report.getAlarmTime());
            form.setLocation(report.getLocation());
            form.setPostalCode(report.getPostalCode());
            form.setDistrict(report.getDistrict());
            form.setStreet(report.getStreet());
            form.setHouseNumber(report.getHouseNumber());
            form.setAlarmierungDurch(report.getAlarmierungDurch());
        }
        KraefteFahrzeugeState kraefteState = einsatzberichtService.buildKraefteFahrzeugeState(unitId, reportId);
        model.addAttribute("report", report);
        model.addAttribute("form", form);
        model.addAttribute("unitPersons", einsatzberichtService.listPersonsForForm(unitId));
        model.addAttribute("knownStichworte", einsatzberichtService.listKnownStichworte(unitId));
        model.addAttribute("kraefteState", kraefteState);
        model.addAttribute("kraefteInitialJson", einsatzberichtService.serializeKraefteFahrzeugeState(kraefteState));
        if (form.getCrewAssignmentsJson() == null || form.getCrewAssignmentsJson().isBlank()) {
            form.setCrewAssignmentsJson(buildCrewJson(kraefteState));
        }
    }

    private static String buildCrewJson(KraefteFahrzeugeState state) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        first = appendCrewAssignment(sb, state.einsatzstelle(), first);
        first = appendCrewAssignment(sb, state.wache(), first);
        for (KraefteFahrzeugeState.KraefteVehicleView vehicle : state.vehicles()) {
            first = appendCrewAssignment(sb, vehicle, first);
        }
        sb.append(']');
        return sb.toString();
    }

    private static boolean appendCrewAssignment(
            StringBuilder sb, KraefteFahrzeugeState.KraefteVehicleView vehicle, boolean first) {
        if (!first) {
            sb.append(',');
        }
        sb.append("{\"vehicleId\":").append(vehicle.vehicleId()).append(",\"personIds\":[");
        List<Long> ids = vehicle.crewPersonIds();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids.get(i));
        }
        sb.append(']');
        if (vehicle.einheitsfuehrerPersonId() != null) {
            sb.append(",\"einheitsfuehrerPersonId\":").append(vehicle.einheitsfuehrerPersonId());
        }
        if (vehicle.maschinistPersonId() != null) {
            sb.append(",\"maschinistPersonId\":").append(vehicle.maschinistPersonId());
        }
        List<Long> paIds = vehicle.crewPersons().stream()
                .filter(KraefteFahrzeugeState.KraeftePersonView::usesPa)
                .map(KraefteFahrzeugeState.KraeftePersonView::id)
                .toList();
        if (!paIds.isEmpty()) {
            sb.append(",\"paPersonIds\":[");
            for (int i = 0; i < paIds.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(paIds.get(i));
            }
            sb.append(']');
        }
        sb.append('}');
        return false;
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

    private Unit resolveUnit(Long unitId, AppUserDetails actor) {
        Unit unit = unitService
                .resolveActiveUnit(unitId, actor)
                .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
        accessControlService.requireUnitAccess(actor, unit.getId());
        return unit;
    }

    private void requireModuleEnabled(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.BERICHTE, unitId)) {
            throw new IllegalArgumentException("Das Modul Berichte ist für diese Einheit nicht aktiviert.");
        }
    }

    private void requireBerichteRead(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "berichte.read");
    }

    private void requireBerichteWrite(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "berichte.write");
    }

    private boolean canWrite(AppUserDetails actor, long unitId) {
        return userPermissionService.hasPermission(actor, unitId, "berichte.write");
    }

    private static String redirectHome(Long unitId) {
        return unitId != null ? "redirect:/?unit=" + unitId : "redirect:/";
    }

    private static String redirectBerichte(Long unitId, String tab) {
        if (unitId != null) {
            return "redirect:/berichte?unit=" + unitId + "&tab=" + tab;
        }
        return "redirect:/berichte?tab=" + tab;
    }

    private static String sanitizeReturnUrl(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()) {
            return null;
        }
        String trimmed = returnUrl.trim();
        if (!trimmed.startsWith("/") || trimmed.startsWith("//")) {
            return null;
        }
        return trimmed;
    }

    private static String buildBackUrl(String returnPath, long unitId) {
        if (returnPath == null) {
            return null;
        }
        return returnPath + (returnPath.contains("?") ? "&" : "?") + "unit=" + unitId;
    }
}
