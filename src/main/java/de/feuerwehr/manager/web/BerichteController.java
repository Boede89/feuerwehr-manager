package de.feuerwehr.manager.web;

import de.feuerwehr.manager.berichte.AnwesenheitslisteAccess;
import de.feuerwehr.manager.berichte.AnwesenheitslisteListResponse;
import de.feuerwehr.manager.berichte.AnwesenheitFormBundle;
import de.feuerwehr.manager.berichte.KraefteCrewJsonSupport;
import de.feuerwehr.manager.berichte.AnwesenheitslisteService;
import de.feuerwehr.manager.berichte.AnwesenheitslisteTerminSyncService;
import de.feuerwehr.manager.berichte.AttendanceReport;
import de.feuerwehr.manager.berichte.BerichteTab;
import de.feuerwehr.manager.berichte.CrewAssignment;
import de.feuerwehr.manager.berichte.DeployedEquipmentAssignment;
import de.feuerwehr.manager.berichte.EinsatzberichtAccess;
import de.feuerwehr.manager.berichte.EinsatzberichtForm;
import de.feuerwehr.manager.berichte.EinsatzberichtService;
import de.feuerwehr.manager.berichte.EinsatzberichtListResponse;
import de.feuerwehr.manager.berichte.EinsatzberichtPdfService;
import de.feuerwehr.manager.berichte.ForeignPersonOption;
import de.feuerwehr.manager.berichte.ForeignUnitOption;
import de.feuerwehr.manager.berichte.IncidentReport;
import de.feuerwehr.manager.berichte.IncidentReportStatus;
import de.feuerwehr.manager.berichte.DamagePerpetratorSupport;
import de.feuerwehr.manager.berichte.PersonDamageDetailsSupport;
import de.feuerwehr.manager.berichte.VehicleEquipmentView;
import de.feuerwehr.manager.pdf.PdfDownloadResponse;
import de.feuerwehr.manager.divera.DiveraEinsatzberichtSyncService;
import de.feuerwehr.manager.berichte.KraefteFahrzeugeState;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
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
    private final EinsatzberichtPdfService einsatzberichtPdfService;
    private final DiveraEinsatzberichtSyncService diveraEinsatzberichtSyncService;
    private final AnwesenheitslisteService anwesenheitslisteService;
    private final AnwesenheitslisteTerminSyncService anwesenheitslisteTerminSyncService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "tab", defaultValue = "einsatz") String tab,
            @RequestParam(name = "year", required = false) Integer filterYear,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireBerichteRead(actor, unit.getId());
            BerichteTab berichteTab = BerichteTab.fromKey(tab);
            model.addAttribute("berichteTab", berichteTab.key());
            model.addAttribute("berichteTabs", BerichteTab.values());
            boolean canApprove = canApprove(actor, unit.getId());
            model.addAttribute("canWrite", canWrite(actor, unit.getId()));
            model.addAttribute("canApprove", canApprove);
            if (berichteTab == BerichteTab.EINSATZ) {
                DiveraEinsatzberichtSyncService.SyncResult sync =
                        diveraEinsatzberichtSyncService.syncAlarmsForUnit(unit.getId());
                if (sync.success() && sync.created() > 0) {
                    model.addAttribute(
                            "message",
                            sync.created() + " Einsatzbericht/Einsatzberichte aus DIVERA als Entwurf übernommen.");
                }
                model.addAttribute("filterYear", filterYear != null ? filterYear : LocalDate.now().getYear());
            } else if (berichteTab == BerichteTab.ANWESENHEIT) {
                AnwesenheitslisteTerminSyncService.SyncResult sync =
                        anwesenheitslisteTerminSyncService.syncTerminsForUnit(unit.getId());
                if (sync.success() && sync.created() > 0) {
                    model.addAttribute("message", sync.message());
                }
                model.addAttribute("filterYear", filterYear != null ? filterYear : LocalDate.now().getYear());
            }
            return "berichte/index";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectHome(unitId);
        }
    }

    @GetMapping("/einsatzberichte/list")
    @ResponseBody
    public EinsatzberichtListResponse listEinsatzberichte(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "year", required = false) Integer year) {
        Unit unit = resolveUnit(unitId, actor);
        requireModuleEnabled(unit.getId());
        requireBerichteRead(actor, unit.getId());
        int filterYear = year != null ? year : LocalDate.now().getYear();
        return einsatzberichtService.listForYear(unit.getId(), filterYear);
    }

    @GetMapping("/einsatzberichte/{id}/modal")
    public String modalEinsatzbericht(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @PathVariable long id,
            Model model) {
        Unit unit = resolveUnit(unitId, actor, model);
        requireModuleEnabled(unit.getId());
        requireBerichteRead(actor, unit.getId());
        IncidentReport report = einsatzberichtService.requireReport(unit.getId(), id);
        EinsatzberichtForm form = EinsatzberichtForm.fromReport(report);
        populateEinsatzFormModel(model, unit.getId(), report, form, false);
        model.addAttribute("formMode", "view");
        boolean canApprove = canApprove(actor, unit.getId());
        model.addAttribute("canEditReport", EinsatzberichtAccess.canEdit(report, actor, canApprove));
        model.addAttribute("canRelease", EinsatzberichtAccess.canRelease(report, canApprove, actor));
        model.addAttribute("canArchive", EinsatzberichtAccess.canArchive(report, canApprove, actor));
        String stichwort = report.getStichwort() != null && !report.getStichwort().isBlank()
                ? report.getStichwort()
                : report.getIncidentTypeLabel();
        String number = report.getIncidentNumber() != null ? report.getIncidentNumber() : String.valueOf(id);
        model.addAttribute("modalTitle", number + " — " + stichwort);
        return "berichte/einsatzbericht-modal-body";
    }

    @GetMapping("/einsatzberichte/suggest-number")
    @ResponseBody
    public String suggestIncidentNumber(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Unit unit = resolveUnit(unitId, actor);
        requireModuleEnabled(unit.getId());
        requireBerichteRead(actor, unit.getId());
        return einsatzberichtService.suggestIncidentNumber(unit.getId(), date);
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
            boolean canApprove = canApprove(actor, unit.getId());
            model.addAttribute("canWrite", canWrite(actor, unit.getId()));
            model.addAttribute("canApprove", canApprove);
            model.addAttribute("canEditReport", EinsatzberichtAccess.canEdit(report, actor, canApprove));
            model.addAttribute("canRelease", EinsatzberichtAccess.canRelease(report, canApprove, actor));
            model.addAttribute("canArchive", EinsatzberichtAccess.canArchive(report, canApprove, actor));
            String safeReturn = sanitizeReturnUrl(returnUrl);
            model.addAttribute("einsatzListPath", safeReturn);
            model.addAttribute("backUrl", buildBackUrl(safeReturn, unit.getId()));
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
            requireBerichteRead(actor, unit.getId());
            IncidentReport report = einsatzberichtService.requireReport(unit.getId(), id);
            boolean canApprove = canApprove(actor, unit.getId());
            if (!EinsatzberichtAccess.canEdit(report, actor, canApprove)) {
                throw new IllegalArgumentException("Dieser Einsatzbericht kann nicht bearbeitet werden.");
            }
            EinsatzberichtForm form = EinsatzberichtForm.fromReport(report);
            populateEinsatzFormModel(model, unit.getId(), report, form, true);
            model.addAttribute("formMode", "edit");
            model.addAttribute("canDeleteReport", EinsatzberichtAccess.canDelete(report, actor, canApprove));
            model.addAttribute("pageTitle", "Einsatzbericht bearbeiten");
            model.addAttribute(
                    "pageSubtitle",
                    (report.getIncidentNumber() != null ? report.getIncidentNumber() : "Entwurf")
                            + " · "
                            + report.getStatus().label());
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
            List<DeployedEquipmentAssignment> deployedEquipment =
                    einsatzberichtService.parseDeployedEquipment(form.getDeployedEquipmentJson());
            einsatzberichtService.create(unit.getId(), form.toData(crewAssignments, deployedEquipment), actor);
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
            requireBerichteRead(actor, unit.getId());
            boolean canApprove = canApprove(actor, unit.getId());
            List<CrewAssignment> crewAssignments = einsatzberichtService.parseCrewAssignments(form.getCrewAssignmentsJson());
            List<DeployedEquipmentAssignment> deployedEquipment =
                    einsatzberichtService.parseDeployedEquipment(form.getDeployedEquipmentJson());
            einsatzberichtService.update(
                    unit.getId(),
                    id,
                    form.toData(crewAssignments, deployedEquipment),
                    form.getChangeComment(),
                    actor,
                    canApprove);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Einsatzbericht wurde aktualisiert.");
            return redirectBerichte(unit.getId(), "einsatz", null, null, null);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, "einsatz", null, null, null);
        }
    }

    @PostMapping("/einsatzberichte/{id}/delete")
    public String deleteEinsatzbericht(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "year", required = false) Integer filterYear,
            @RequestParam(name = "stichwort", required = false) String filterStichwort,
            @RequestParam(name = "status", required = false) String filterStatus,
            @PathVariable long id,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor);
            requireModuleEnabled(unit.getId());
            requireBerichteRead(actor, unit.getId());
            boolean canApprove = canApprove(actor, unit.getId());
            einsatzberichtService.delete(unit.getId(), id, actor, canApprove);
            redirectAttributes.addFlashAttribute("message", "Einsatzbericht wurde gelöscht.");
            return redirectBerichte(unit.getId(), "einsatz", filterYear, filterStichwort, filterStatus);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, "einsatz", filterYear, filterStichwort, filterStatus);
        }
    }

    @PostMapping("/einsatzberichte/{id}/freigeben")
    public String releaseEinsatzbericht(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "returnUrl", required = false) String returnUrl,
            @PathVariable long id,
            RedirectAttributes redirectAttributes) {
        return changeEinsatzberichtStatus(
                actor, unitId, id, IncidentReportStatus.FREIGEGEBEN, returnUrl, redirectAttributes, "freigeben");
    }

    @PostMapping("/einsatzberichte/{id}/archivieren")
    public String archiveEinsatzbericht(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "returnUrl", required = false) String returnUrl,
            @PathVariable long id,
            RedirectAttributes redirectAttributes) {
        return changeEinsatzberichtStatus(
                actor, unitId, id, IncidentReportStatus.ARCHIVIERT, returnUrl, redirectAttributes, "archivieren");
    }

    private String changeEinsatzberichtStatus(
            AppUserDetails actor,
            Long unitId,
            long id,
            IncidentReportStatus newStatus,
            String returnUrl,
            RedirectAttributes redirectAttributes,
            String actionLabel) {
        try {
            Unit unit = resolveUnit(unitId, actor);
            requireModuleEnabled(unit.getId());
            requireBerichteRead(actor, unit.getId());
            boolean canApprove = canApprove(actor, unit.getId());
            einsatzberichtService.transitionStatus(unit.getId(), id, newStatus, actor, canApprove);
            redirectAttributes.addFlashAttribute("message", "Einsatzbericht wurde " + actionLabel + ".");
            String safeReturn = sanitizeReturnUrl(returnUrl);
            if (safeReturn != null) {
                return "redirect:" + buildBackUrl(safeReturn, unit.getId());
            }
            return redirectBerichte(unit.getId(), "einsatz", null, null, null);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            String safeReturn = sanitizeReturnUrl(returnUrl);
            if (safeReturn != null && unitId != null) {
                return "redirect:" + buildBackUrl(safeReturn, unitId);
            }
            return redirectBerichte(unitId, "einsatz", null, null, null);
        }
    }

    @GetMapping("/anwesenheitslisten/list")
    @ResponseBody
    public AnwesenheitslisteListResponse listAnwesenheitslisten(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "year", required = false) Integer year) {
        Unit unit = resolveUnit(unitId, actor);
        requireModuleEnabled(unit.getId());
        requireBerichteRead(actor, unit.getId());
        int filterYear = year != null ? year : LocalDate.now().getYear();
        return anwesenheitslisteService.listForYear(unit.getId(), filterYear);
    }

    @GetMapping("/anwesenheitslisten/suggest-number")
    @ResponseBody
    public String suggestAttendanceNumber(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Unit unit = resolveUnit(unitId, actor);
        requireModuleEnabled(unit.getId());
        requireBerichteRead(actor, unit.getId());
        return anwesenheitslisteService.suggestReportNumber(unit.getId(), date);
    }

    @GetMapping("/anwesenheitslisten/neu")
    public String newAnwesenheitsliste(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireBerichteWrite(actor, unit.getId());
            applyAnwesenheitFormBundle(model, unit.getId(), anwesenheitslisteService.buildFormBundle(unit.getId(), null));
            model.addAttribute("formMode", "create");
            model.addAttribute("pageTitle", "Neue Anwesenheitsliste");
            model.addAttribute("pageSubtitle", "Entwurf — wird nach dem Speichern zur Freigabe vorgelegt");
            return "berichte/anwesenheitsliste-form";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, "anwesenheit");
        }
    }

    @GetMapping("/anwesenheitslisten/{id}/modal")
    public String modalAnwesenheitsliste(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @PathVariable long id,
            Model model) {
        Unit unit = resolveUnit(unitId, actor, model);
        requireModuleEnabled(unit.getId());
        requireBerichteRead(actor, unit.getId());
        AnwesenheitFormBundle bundle = anwesenheitslisteService.buildFormBundle(unit.getId(), id);
        AttendanceReport report = bundle.report();
        applyAnwesenheitFormBundle(model, unit.getId(), bundle);
        model.addAttribute("formMode", "view");
        boolean canApprove = canApprove(actor, unit.getId());
        model.addAttribute("canEditReport", AnwesenheitslisteAccess.canEdit(report, actor, canApprove));
        model.addAttribute("canRelease", AnwesenheitslisteAccess.canRelease(report, canApprove, actor));
        model.addAttribute("canArchive", AnwesenheitslisteAccess.canArchive(report, canApprove, actor));
        String title = report.getTitle() != null && !report.getTitle().isBlank() ? report.getTitle() : "Anwesenheitsliste";
        String dateLabel = report.getEventDate() != null
                ? report.getEventDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                : "";
        model.addAttribute(
                "modalTitle",
                dateLabel.isEmpty() ? title : title + " — " + dateLabel);
        return "berichte/anwesenheitsliste-modal-body";
    }

    @GetMapping("/anwesenheitslisten/{id}")
    public String viewAnwesenheitsliste(
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
            AnwesenheitFormBundle bundle = anwesenheitslisteService.buildFormBundle(unit.getId(), id);
            AttendanceReport report = bundle.report();
            applyAnwesenheitFormBundle(model, unit.getId(), bundle);
            model.addAttribute("formMode", "view");
            boolean canApprove = canApprove(actor, unit.getId());
            model.addAttribute("canWrite", canWrite(actor, unit.getId()));
            model.addAttribute("canApprove", canApprove);
            model.addAttribute("canEditReport", AnwesenheitslisteAccess.canEdit(report, actor, canApprove));
            model.addAttribute("canRelease", AnwesenheitslisteAccess.canRelease(report, canApprove, actor));
            model.addAttribute("canArchive", AnwesenheitslisteAccess.canArchive(report, canApprove, actor));
            String safeReturn = sanitizeReturnUrl(returnUrl);
            model.addAttribute("anwesenheitListPath", safeReturn);
            model.addAttribute("backUrl", buildBackUrl(safeReturn, unit.getId()));
            model.addAttribute("pageTitle", "Anwesenheitsliste");
            model.addAttribute(
                    "pageSubtitle",
                    report.getReportNumber() != null ? report.getReportNumber() : "Anzeige");
            return "berichte/anwesenheitsliste-view";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            String safeReturn = sanitizeReturnUrl(returnUrl);
            if (safeReturn != null) {
                return "redirect:" + safeReturn;
            }
            return redirectBerichte(unitId, "anwesenheit");
        }
    }

    @GetMapping("/anwesenheitslisten/{id}/bearbeiten")
    public String editAnwesenheitsliste(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @PathVariable long id,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireBerichteRead(actor, unit.getId());
            AttendanceReport report = anwesenheitslisteService.requireReport(unit.getId(), id);
            boolean canApprove = canApprove(actor, unit.getId());
            if (!AnwesenheitslisteAccess.canEdit(report, actor, canApprove)) {
                throw new IllegalArgumentException("Diese Anwesenheitsliste kann nicht bearbeitet werden.");
            }
            applyAnwesenheitFormBundle(model, unit.getId(), anwesenheitslisteService.buildFormBundle(unit.getId(), id));
            model.addAttribute("formMode", "edit");
            model.addAttribute("canDeleteReport", AnwesenheitslisteAccess.canDelete(report, actor, canApprove));
            model.addAttribute("pageTitle", "Anwesenheitsliste bearbeiten");
            model.addAttribute(
                    "pageSubtitle",
                    (report.getReportNumber() != null ? report.getReportNumber() : "Entwurf")
                            + " · "
                            + report.getStatus().label());
            return "berichte/anwesenheitsliste-form";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, "anwesenheit");
        }
    }

    @PostMapping("/anwesenheitslisten")
    public String createAnwesenheitsliste(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @ModelAttribute EinsatzberichtForm form,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor);
            requireModuleEnabled(unit.getId());
            requireBerichteWrite(actor, unit.getId());
            List<CrewAssignment> crewAssignments =
                    einsatzberichtService.parseCrewAssignments(form.getCrewAssignmentsJson());
            anwesenheitslisteService.createFromEinsatzForm(unit.getId(), form, crewAssignments, actor);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Anwesenheitsliste wurde gespeichert.");
            return redirectBerichte(unit.getId(), "anwesenheit");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, "anwesenheit");
        }
    }

    @PostMapping("/anwesenheitslisten/{id}")
    public String updateAnwesenheitsliste(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @PathVariable long id,
            @ModelAttribute EinsatzberichtForm form,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor);
            requireModuleEnabled(unit.getId());
            requireBerichteRead(actor, unit.getId());
            boolean canApprove = canApprove(actor, unit.getId());
            List<CrewAssignment> crewAssignments =
                    einsatzberichtService.parseCrewAssignments(form.getCrewAssignmentsJson());
            anwesenheitslisteService.updateFromEinsatzForm(
                    unit.getId(), id, form, crewAssignments, actor, canApprove);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Anwesenheitsliste wurde aktualisiert.");
            return redirectBerichte(unit.getId(), "anwesenheit");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, "anwesenheit");
        }
    }

    @PostMapping("/anwesenheitslisten/{id}/delete")
    public String deleteAnwesenheitsliste(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "year", required = false) Integer filterYear,
            @RequestParam(name = "status", required = false) String filterStatus,
            @PathVariable long id,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor);
            requireModuleEnabled(unit.getId());
            requireBerichteRead(actor, unit.getId());
            boolean canApprove = canApprove(actor, unit.getId());
            anwesenheitslisteService.delete(unit.getId(), id, actor, canApprove);
            redirectAttributes.addFlashAttribute("message", "Anwesenheitsliste wurde gelöscht.");
            return redirectBerichte(unit.getId(), "anwesenheit", filterYear, null, filterStatus);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, "anwesenheit", filterYear, null, filterStatus);
        }
    }

    @PostMapping("/anwesenheitslisten/{id}/freigeben")
    public String releaseAnwesenheitsliste(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "returnUrl", required = false) String returnUrl,
            @PathVariable long id,
            RedirectAttributes redirectAttributes) {
        return changeAnwesenheitslisteStatus(
                actor, unitId, id, IncidentReportStatus.FREIGEGEBEN, returnUrl, redirectAttributes, "freigegeben");
    }

    @PostMapping("/anwesenheitslisten/{id}/archivieren")
    public String archiveAnwesenheitsliste(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "returnUrl", required = false) String returnUrl,
            @PathVariable long id,
            RedirectAttributes redirectAttributes) {
        return changeAnwesenheitslisteStatus(
                actor, unitId, id, IncidentReportStatus.ARCHIVIERT, returnUrl, redirectAttributes, "archiviert");
    }

    private String changeAnwesenheitslisteStatus(
            AppUserDetails actor,
            Long unitId,
            long id,
            IncidentReportStatus newStatus,
            String returnUrl,
            RedirectAttributes redirectAttributes,
            String actionLabel) {
        try {
            Unit unit = resolveUnit(unitId, actor);
            requireModuleEnabled(unit.getId());
            requireBerichteRead(actor, unit.getId());
            boolean canApprove = canApprove(actor, unit.getId());
            anwesenheitslisteService.transitionStatus(unit.getId(), id, newStatus, actor, canApprove);
            redirectAttributes.addFlashAttribute("message", "Anwesenheitsliste wurde " + actionLabel + ".");
            String safeReturn = sanitizeReturnUrl(returnUrl);
            if (safeReturn != null) {
                return "redirect:" + buildBackUrl(safeReturn, unit.getId());
            }
            return redirectBerichte(unit.getId(), "anwesenheit");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            String safeReturn = sanitizeReturnUrl(returnUrl);
            if (safeReturn != null && unitId != null) {
                return "redirect:" + buildBackUrl(safeReturn, unitId);
            }
            return redirectBerichte(unitId, "anwesenheit");
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

    @GetMapping("/anwesenheitslisten/foreign-units")
    @ResponseBody
    public List<ForeignUnitOption> anwesenheitForeignUnits(
            @AuthenticationPrincipal AppUserDetails actor, @RequestParam(name = "unit") long unitId) {
        accessControlService.requireUnitAccess(actor, unitId);
        requireModuleEnabled(unitId);
        requireBerichteRead(actor, unitId);
        return einsatzberichtService.listForeignUnits(unitId);
    }

    @GetMapping("/anwesenheitslisten/foreign-personnel")
    @ResponseBody
    public List<ForeignPersonOption> anwesenheitForeignPersonnel(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestParam(name = "sourceUnit") long sourceUnitId,
            @RequestParam(name = "q", defaultValue = "") String query) {
        accessControlService.requireUnitAccess(actor, unitId);
        requireModuleEnabled(unitId);
        requireBerichteRead(actor, unitId);
        return einsatzberichtService.listForeignPersonnel(unitId, sourceUnitId, query);
    }

    @GetMapping("/anwesenheitslisten/vehicle-equipment")
    @ResponseBody
    public List<VehicleEquipmentView> anwesenheitVehicleEquipment(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestParam(name = "vehicleIds") List<Long> vehicleIds) {
        accessControlService.requireUnitAccess(actor, unitId);
        requireModuleEnabled(unitId);
        return einsatzberichtService.listVehicleEquipment(unitId, vehicleIds);
    }

    private void applyAnwesenheitFormBundle(Model model, long unitId, AnwesenheitFormBundle bundle) {
        model.addAttribute("report", bundle.report());
        model.addAttribute("form", bundle.form());
        model.addAttribute("unitPersons", bundle.unitPersons());
        model.addAttribute("knownStichworte", bundle.knownStichworte());
        model.addAttribute("kraefteState", bundle.kraefteState());
        model.addAttribute("kraefteInitialJson", bundle.kraefteInitialJson());
        model.addAttribute("allowForeignUnitPersonnel", bundle.allowForeignUnitPersonnel());
        model.addAttribute("unitAddressJson", anwesenheitslisteService.buildUnitAddressJson(unitId));
        model.addAttribute("showChangeHistory", false);
        model.addAttribute("reportChanges", List.of());
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
        model.addAttribute(
                "allowForeignUnitPersonnel", einsatzberichtService.isForeignUnitPersonnelAllowed(unitId));
        if (form.getCrewAssignmentsJson() == null || form.getCrewAssignmentsJson().isBlank()) {
            form.setCrewAssignmentsJson(buildCrewJson(kraefteState));
        }
        if (form.getDeployedEquipmentJson() == null || form.getDeployedEquipmentJson().isBlank()) {
            form.setDeployedEquipmentJson(
                    reportId != null ? einsatzberichtService.buildDeployedEquipmentJson(reportId) : "[]");
        }
        if (form.getPersonDamageDetailsJson() == null || form.getPersonDamageDetailsJson().isBlank()) {
            form.setPersonDamageDetailsJson(PersonDamageDetailsSupport.emptyJson());
        }
        if (form.getDamagePerpetratorJson() == null || form.getDamagePerpetratorJson().isBlank()) {
            form.setDamagePerpetratorJson(DamagePerpetratorSupport.emptyJson());
        }
        boolean showHistory = EinsatzberichtAccess.showChangeHistory(report);
        model.addAttribute("showChangeHistory", showHistory);
        model.addAttribute(
                "reportChanges",
                showHistory && report != null
                        ? einsatzberichtService.listChanges(unitId, report.getId())
                        : List.of());
    }

    @GetMapping("/einsatzberichte/{id}/pdf")
    public Object downloadEinsatzberichtPdf(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @PathVariable long id,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor);
            requireModuleEnabled(unit.getId());
            requireBerichteRead(actor, unit.getId());
            IncidentReport report = einsatzberichtService.requireReport(unit.getId(), id);
            byte[] pdf = einsatzberichtPdfService.renderPdf(unit.getId(), id);
            return PdfDownloadResponse.attachment(einsatzberichtPdfService.suggestedFilename(report), pdf);
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBerichte(unitId, "einsatz");
        }
    }

    @GetMapping("/einsatzberichte/foreign-units")
    @ResponseBody
    public List<ForeignUnitOption> foreignUnits(
            @AuthenticationPrincipal AppUserDetails actor, @RequestParam(name = "unit") long unitId) {
        accessControlService.requireUnitAccess(actor, unitId);
        requireModuleEnabled(unitId);
        requireBerichteRead(actor, unitId);
        return einsatzberichtService.listForeignUnits(unitId);
    }

    @GetMapping("/einsatzberichte/foreign-personnel")
    @ResponseBody
    public List<ForeignPersonOption> foreignPersonnel(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestParam(name = "sourceUnit") long sourceUnitId,
            @RequestParam(name = "q", defaultValue = "") String query) {
        accessControlService.requireUnitAccess(actor, unitId);
        requireModuleEnabled(unitId);
        requireBerichteRead(actor, unitId);
        return einsatzberichtService.listForeignPersonnel(unitId, sourceUnitId, query);
    }

    @GetMapping("/einsatzberichte/vehicle-equipment")
    @ResponseBody
    public List<VehicleEquipmentView> vehicleEquipment(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestParam(name = "vehicleIds") List<Long> vehicleIds) {
        accessControlService.requireUnitAccess(actor, unitId);
        requireModuleEnabled(unitId);
        return einsatzberichtService.listVehicleEquipment(unitId, vehicleIds);
    }

    private static String buildCrewJson(KraefteFahrzeugeState state) {
        return KraefteCrewJsonSupport.buildCrewJson(state);
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

    private boolean canApprove(AppUserDetails actor, long unitId) {
        return userPermissionService.hasPermission(actor, unitId, "berichte.approve");
    }

    private static IncidentReportStatus parseStatusFilter(String filterStatus) {
        if (filterStatus == null || filterStatus.isBlank()) {
            return null;
        }
        return IncidentReportStatus.valueOf(filterStatus.trim().toUpperCase());
    }

    private static String buildEinsatzListPath(int year, String stichwort, IncidentReportStatus status) {
        StringBuilder path = new StringBuilder("/berichte?tab=einsatz&year=").append(year);
        if (stichwort != null && !stichwort.isBlank()) {
            path.append("&stichwort=").append(URLEncoder.encode(stichwort.trim(), StandardCharsets.UTF_8));
        }
        if (status != null) {
            path.append("&status=").append(status.name());
        }
        return path.toString();
    }

    private static String redirectHome(Long unitId) {
        return unitId != null ? "redirect:/?unit=" + unitId : "redirect:/";
    }

    private static String redirectBerichte(
            Long unitId, String tab, Integer year, String stichwort, String status) {
        StringBuilder url = new StringBuilder("redirect:/berichte?tab=").append(tab);
        if (unitId != null) {
            url.append("&unit=").append(unitId);
        }
        if (year != null) {
            url.append("&year=").append(year);
        }
        if (stichwort != null && !stichwort.isBlank()) {
            url.append("&stichwort=").append(URLEncoder.encode(stichwort.trim(), StandardCharsets.UTF_8));
        }
        if (status != null && !status.isBlank()) {
            url.append("&status=").append(status.trim().toUpperCase());
        }
        return url.toString();
    }

    private static String redirectBerichte(Long unitId, String tab) {
        return redirectBerichte(unitId, tab, null, null, null);
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
