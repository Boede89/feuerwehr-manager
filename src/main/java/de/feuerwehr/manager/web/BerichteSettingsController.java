package de.feuerwehr.manager.web;

import de.feuerwehr.manager.berichte.BerichteEmailReportType;
import de.feuerwehr.manager.berichte.BerichteEmailSettingsService;
import de.feuerwehr.manager.berichte.BerichteSettingsService;
import de.feuerwehr.manager.berichte.BerichteTab;
import de.feuerwehr.manager.berichte.IncidentReportStatus;
import de.feuerwehr.manager.berichte.UnitBerichteEmailSettings;
import de.feuerwehr.manager.berichte.UnitBerichteSettings;
import de.feuerwehr.manager.divera.UnitDiveraStatusId;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/berichte")
@RequiredArgsConstructor
public class BerichteSettingsController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final BerichteSettingsService berichteSettingsService;
    private final BerichteEmailSettingsService berichteEmailSettingsService;
    private final PersonalService personalService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "tab", defaultValue = "einsatz") String tab,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            Unit unit = unitService
                    .resolveActiveUnit(unitId, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
            accessControlService.requireUnitAccess(actor, unit.getId());
            requireModuleEnabled(unit.getId());
            UnitBerichteSettings settings = berichteSettingsService.ensureSettings(unit.getId());
            List<UnitDiveraStatusId> statusOptions = berichteSettingsService.listSelectableStatusIds(unit.getId());
            BerichteTab berichteTab = BerichteTab.fromKey(tab);
            model.addAttribute("unitId", unit.getId());
            model.addAttribute("currentUnitName", unit.getName());
            model.addAttribute("berichteTab", berichteTab.key());
            model.addAttribute("berichteTabs", BerichteTab.values());
            model.addAttribute("settings", settings);
            model.addAttribute("diveraStatusOptions", statusOptions);
            model.addAttribute(
                    "selectedPersonnelStatusIds", berichteSettingsService.parsePersonnelStatusIds(settings));
            addEmailSettingsModel(model, unit.getId(), berichteTab);
            model.addAttribute("unitPersons", personalService.listPersons(unit.getId()));
            return "settings/berichte";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId != null ? "redirect:/admin?scope=einheit&tab=module&unit=" + unitId : "redirect:/settings";
        }
    }

    @PostMapping("/einsatz")
    public String saveEinsatz(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "importIncidentDataFromDivera", defaultValue = "false") boolean importIncidentDataFromDivera,
            @RequestParam(name = "importPersonnelFromDivera", defaultValue = "false") boolean importPersonnelFromDivera,
            @RequestParam(name = "autoAssignDiveraPersonnelToAnwesenheit", defaultValue = "false")
                    boolean autoAssignDiveraPersonnelToAnwesenheit,
            @RequestParam(name = "allowForeignUnitPersonnel", defaultValue = "false") boolean allowForeignUnitPersonnel,
            @RequestParam(name = "personnelStatusIds", required = false) String[] personnelStatusIds,
            @RequestParam(name = "einsatzReleaseCreateGeraetewart", defaultValue = "false") boolean einsatzReleaseCreateGeraetewart,
            @RequestParam(name = "einsatzReleasePrintReport", defaultValue = "false") boolean einsatzReleasePrintReport,
            @RequestParam(name = "einsatzReleasePrintGeraetewart", defaultValue = "false") boolean einsatzReleasePrintGeraetewart,
            @RequestParam(name = "einsatzReleasePrintMaengel", defaultValue = "false") boolean einsatzReleasePrintMaengel,
            @RequestParam(name = "einsatzEmailEnabled", defaultValue = "false") boolean einsatzEmailEnabled,
            @RequestParam(name = "einsatzSendOnStatus", defaultValue = "FREIGEGEBEN") String einsatzSendOnStatus,
            @RequestParam(name = "einsatzPersonIds", required = false) Long[] einsatzPersonIds,
            @RequestParam(name = "einsatzManualEmails", required = false) String einsatzManualEmails,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            List<String> statusIds = personnelStatusIds == null ? List.of() : Arrays.asList(personnelStatusIds);
            berichteSettingsService.saveEinsatzSettings(
                    unit,
                    importIncidentDataFromDivera,
                    importPersonnelFromDivera,
                    autoAssignDiveraPersonnelToAnwesenheit,
                    allowForeignUnitPersonnel,
                    statusIds,
                    einsatzReleaseCreateGeraetewart,
                    einsatzReleasePrintReport,
                    einsatzReleasePrintGeraetewart,
                    einsatzReleasePrintMaengel);
            saveEmailSettings(
                    unit,
                    BerichteEmailReportType.EINSATZ,
                    einsatzEmailEnabled,
                    einsatzSendOnStatus,
                    einsatzPersonIds,
                    einsatzManualEmails);
            redirectAttributes.addFlashAttribute("message", "Einsatzbericht-Einstellungen gespeichert.");
            return "redirect:/settings/berichte?unit=" + unit + "&tab=einsatz";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/berichte?unit=" + unit + "&tab=einsatz";
        }
    }

    @PostMapping("/anwesenheit")
    public String saveAnwesenheit(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "anwesenheitReleasePrintReport", defaultValue = "false") boolean anwesenheitReleasePrintReport,
            @RequestParam(name = "anwesenheitEmailEnabled", defaultValue = "false") boolean anwesenheitEmailEnabled,
            @RequestParam(name = "anwesenheitSendOnStatus", defaultValue = "FREIGEGEBEN") String anwesenheitSendOnStatus,
            @RequestParam(name = "anwesenheitPersonIds", required = false) Long[] anwesenheitPersonIds,
            @RequestParam(name = "anwesenheitManualEmails", required = false) String anwesenheitManualEmails,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            berichteSettingsService.saveAnwesenheitReleaseSettings(unit, anwesenheitReleasePrintReport);
            saveEmailSettings(
                    unit,
                    BerichteEmailReportType.ANWESENHEIT,
                    anwesenheitEmailEnabled,
                    anwesenheitSendOnStatus,
                    anwesenheitPersonIds,
                    anwesenheitManualEmails);
            redirectAttributes.addFlashAttribute("message", "Anwesenheitslisten-Einstellungen gespeichert.");
            return "redirect:/settings/berichte?unit=" + unit + "&tab=anwesenheit";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/berichte?unit=" + unit + "&tab=anwesenheit";
        }
    }

    @PostMapping("/geraetewart")
    public String saveGeraetewartEmail(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "geraetewartEmailEnabled", defaultValue = "false") boolean emailEnabled,
            @RequestParam(name = "geraetewartPersonIds", required = false) Long[] personIds,
            @RequestParam(name = "geraetewartManualEmails", required = false) String manualEmails,
            RedirectAttributes redirectAttributes) {
        return saveEmailOnlyTab(actor, unit, BerichteEmailReportType.GERAETEWART, emailEnabled, personIds, manualEmails, redirectAttributes);
    }

    @PostMapping("/maengel")
    public String saveMaengelEmail(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "maengelEmailEnabled", defaultValue = "false") boolean emailEnabled,
            @RequestParam(name = "maengelPersonIds", required = false) Long[] personIds,
            @RequestParam(name = "maengelManualEmails", required = false) String manualEmails,
            RedirectAttributes redirectAttributes) {
        return saveEmailOnlyTab(actor, unit, BerichteEmailReportType.MAENGEL, emailEnabled, personIds, manualEmails, redirectAttributes);
    }

    @PostMapping("/checklisten")
    public String saveChecklistenEmail(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "checklistenEmailEnabled", defaultValue = "false") boolean emailEnabled,
            @RequestParam(name = "checklistenPersonIds", required = false) Long[] personIds,
            @RequestParam(name = "checklistenManualEmails", required = false) String manualEmails,
            RedirectAttributes redirectAttributes) {
        return saveEmailOnlyTab(actor, unit, BerichteEmailReportType.CHECKLISTEN, emailEnabled, personIds, manualEmails, redirectAttributes);
    }

    private String saveEmailOnlyTab(
            AppUserDetails actor,
            long unit,
            BerichteEmailReportType reportType,
            boolean emailEnabled,
            Long[] personIds,
            String manualEmails,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            saveEmailSettings(unit, reportType, emailEnabled, null, personIds, manualEmails);
            redirectAttributes.addFlashAttribute("message", reportType.label() + "-Einstellungen gespeichert.");
            return "redirect:/settings/berichte?unit=" + unit + "&tab=" + reportType.tabKey();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/berichte?unit=" + unit + "&tab=" + reportType.tabKey();
        }
    }

    private void addEmailSettingsModel(Model model, long unitId, BerichteTab tab) {
        BerichteEmailReportType reportType = BerichteEmailReportType.fromTab(tab.key());
        UnitBerichteEmailSettings emailSettings = berichteEmailSettingsService.ensureSettings(unitId, reportType);
        model.addAttribute("emailSettings", emailSettings);
        model.addAttribute("emailSettingsPersonIds", berichteEmailSettingsService.parsePersonIds(emailSettings));
        model.addAttribute(
                "emailSettingsManualEmailsText",
                String.join("\n", berichteEmailSettingsService.parseManualEmails(emailSettings)));
        model.addAttribute("emailRecipientCount", berichteEmailSettingsService.recipientCount(emailSettings));
    }

    private void saveEmailSettings(
            long unitId,
            BerichteEmailReportType reportType,
            boolean emailEnabled,
            String sendOnStatusRaw,
            Long[] personIds,
            String manualEmailsRaw) {
        IncidentReportStatus sendOnStatus = null;
        if (reportType.statusTrigger()) {
            try {
                sendOnStatus = IncidentReportStatus.valueOf(
                        sendOnStatusRaw != null ? sendOnStatusRaw.trim().toUpperCase() : "FREIGEGEBEN");
            } catch (IllegalArgumentException e) {
                sendOnStatus = IncidentReportStatus.FREIGEGEBEN;
            }
        }
        List<Long> ids = personIds == null ? List.of() : Arrays.asList(personIds);
        List<String> emails = BerichteEmailSettingsService.parseManualEmailsInput(manualEmailsRaw);
        berichteEmailSettingsService.saveSettings(unitId, reportType, emailEnabled, sendOnStatus, ids, emails);
    }

    private void requireModuleEnabled(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.BERICHTE, unitId)) {
            throw new IllegalArgumentException("Das Modul Berichte ist für diese Einheit nicht aktiviert.");
        }
    }
}
