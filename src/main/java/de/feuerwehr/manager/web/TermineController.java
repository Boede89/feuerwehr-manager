package de.feuerwehr.manager.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.personal.PersonalGroupService;
import de.feuerwehr.manager.personal.PersonalInstructorGroupService;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.termine.CreateDienstplanTerminRequest;
import de.feuerwehr.manager.termine.MeineTerminView;
import de.feuerwehr.manager.termine.TermineService;
import de.feuerwehr.manager.termine.TermineTab;
import java.util.List;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.web.dto.ActionResultDto;
import lombok.RequiredArgsConstructor;
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

@Controller
@RequestMapping("/termine")
@RequiredArgsConstructor
public class TermineController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final UserPermissionService userPermissionService;
    private final TermineService termineService;
    private final PersonalService personalService;
    private final PersonalGroupService personalGroupService;
    private final PersonalInstructorGroupService personalInstructorGroupService;
    private final PersonRepository personRepository;
    private final TestModeService testModeService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "tab", defaultValue = "meine") String tab,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireTermineRead(actor, unit.getId());
            TermineTab termineTab = TermineTab.fromKey(tab);
            model.addAttribute("termineTab", termineTab.key());
            model.addAttribute("termineTabs", TermineTab.values());
            model.addAttribute("canWrite", canWrite(actor, unit.getId()));
            if (termineTab == TermineTab.MEINE) {
                addMyTermineModel(actor, unit.getId(), model);
            }
            if (termineTab == TermineTab.DIENSTPLAN) {
                model.addAttribute("dienstplanTermine", termineService.listDienstplanTermine(unit.getId()));
                model.addAttribute("knownDienstplanThemen", termineService.listKnownDienstplanThemen(unit.getId()));
                model.addAttribute("unitPersons", personalService.listPersons(unit.getId()));
                model.addAttribute("unitPersonGroups", personalGroupService.listGroups(unit.getId()));
                model.addAttribute(
                        "instructorGroupsJson",
                        personalInstructorGroupService.serializeGroupsForTerminJson(unit.getId()));
            }
            return "termine/index";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectHome(unitId);
        }
    }

    @PostMapping("/api/dienstplan")
    @ResponseBody
    public ActionResultDto createDienstplanTermin(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestBody CreateDienstplanTerminRequest body) {
        try {
            requireModuleEnabled(unitId);
            requireTermineWrite(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            termineService.createDienstplanTermin(unitId, actor.getUserId(), body);
            return ActionResultDto.success("Termin wurde erstellt.");
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @PutMapping("/api/dienstplan/{terminId}")
    @ResponseBody
    public ActionResultDto updateDienstplanTermin(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long terminId,
            @RequestBody CreateDienstplanTerminRequest body) {
        try {
            requireModuleEnabled(unitId);
            requireTermineWrite(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            termineService.updateDienstplanTermin(unitId, terminId, body);
            return ActionResultDto.success("Termin wurde gespeichert.");
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @DeleteMapping("/api/dienstplan/{terminId}")
    @ResponseBody
    public ActionResultDto deleteDienstplanTermin(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long terminId) {
        try {
            requireModuleEnabled(unitId);
            requireTermineWrite(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            termineService.deleteDienstplanTermin(unitId, terminId);
            return ActionResultDto.success("Termin wurde gelöscht.");
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
        if (!moduleSettingsService.isEnabled(AppModule.TERMINE, unitId)) {
            throw new IllegalArgumentException("Das Modul Termine ist für diese Einheit nicht aktiviert.");
        }
    }

    private void requireTermineRead(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "termine.read");
    }

    private void requireTermineWrite(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "termine.write");
    }

    private boolean canWrite(AppUserDetails actor, long unitId) {
        return userPermissionService.hasPermission(actor, unitId, "termine.write");
    }

    private void addMyTermineModel(AppUserDetails actor, long unitId, Model model) {
        var linkedPerson = personRepository.findActiveByUserIdAndUnitId(
                actor.getUserId(), unitId, testModeService.isEnabled());
        boolean hasLinkedPerson = linkedPerson.isPresent();
        model.addAttribute("hasLinkedPerson", hasLinkedPerson);
        List<MeineTerminView> myTermine = linkedPerson
                .map(person -> termineService.listMyTermine(unitId, person.getId()))
                .orElse(List.of());
        model.addAttribute("myTermine", myTermine);
        model.addAttribute("myTermineJson", serializeMyTermineJson(myTermine));
    }

    private String serializeMyTermineJson(List<MeineTerminView> termine) {
        try {
            return objectMapper.writeValueAsString(termine);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static String redirectHome(Long unitId) {
        return unitId != null ? "redirect:/?unit=" + unitId : "redirect:/";
    }
}
