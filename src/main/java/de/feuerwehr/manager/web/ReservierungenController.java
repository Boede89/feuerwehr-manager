package de.feuerwehr.manager.web;

import de.feuerwehr.manager.reservierungen.CreateReservationRequest;
import de.feuerwehr.manager.reservierungen.ProcessReservationRequest;
import de.feuerwehr.manager.reservierungen.ReservierungenConflictService;
import de.feuerwehr.manager.reservierungen.ReservierungenService;
import de.feuerwehr.manager.reservierungen.ReservierungenTab;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitAdminService;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.user.UserRepository;
import de.feuerwehr.manager.web.dto.ActionResultDto;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/reservierungen")
@RequiredArgsConstructor
public class ReservierungenController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final UserPermissionService userPermissionService;
    private final ReservierungenService reservierungenService;
    private final ReservierungenConflictService conflictService;
    private final UnitAdminService unitAdminService;
    private final UserRepository userRepository;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "tab", defaultValue = "uebersicht") String tab,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Unit unit = resolveUnit(unitId, actor, model);
            requireModuleEnabled(unit.getId());
            requireRead(actor, unit.getId());
            ReservierungenTab activeTab = ReservierungenTab.fromKey(tab);
            boolean canWrite = canWrite(actor, unit.getId());
            model.addAttribute("reservierungenTab", activeTab.key());
            model.addAttribute("reservierungenTabs", ReservierungenTab.values());
            model.addAttribute("canWrite", canWrite);
            model.addAttribute("canManage", canWrite);
            model.addAttribute("vehicles", conflictService.listBookableVehicles(unit.getId()));
            model.addAttribute("rooms", unitAdminService.listRooms(unit.getId()).stream().filter(r -> r.isActive()).toList());
            model.addAttribute("requesterName", actor.getDisplayName());
            model.addAttribute(
                    "requesterEmail",
                    userRepository.findById(actor.getUserId()).map(u -> u.getLoginEmail()).orElse(""));
            if (activeTab == ReservierungenTab.MEINE) {
                model.addAttribute("myReservations", reservierungenService.listMine(unit.getId(), actor.getUserId()));
            }
            if (activeTab == ReservierungenTab.VERWALTUNG && canWrite) {
                model.addAttribute("pendingReservations", reservierungenService.listPending(unit.getId(), actor.getUserId()));
                model.addAttribute("allReservations", reservierungenService.listAll(unit.getId(), actor.getUserId()));
            }
            return "reservierungen/index";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectHome(unitId);
        }
    }

    @PostMapping("/api/fahrzeuge")
    @ResponseBody
    public ActionResultDto createVehicle(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestBody CreateReservationRequest body) {
        try {
            requireModuleEnabled(unitId);
            requireRead(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            reservierungenService.createVehicleReservation(unitId, actor.getUserId(), body);
            return ActionResultDto.success("Fahrzeugreservierung wurde eingereicht.");
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @PostMapping("/api/raeume")
    @ResponseBody
    public ActionResultDto createRoom(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestBody CreateReservationRequest body) {
        try {
            requireModuleEnabled(unitId);
            requireRead(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            reservierungenService.createRoomReservation(unitId, actor.getUserId(), body);
            return ActionResultDto.success("Raumreservierung wurde eingereicht.");
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @PostMapping("/api/fahrzeuge/{id}/process")
    @ResponseBody
    public ActionResultDto processVehicle(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long id,
            @RequestBody ProcessReservationRequest body) {
        try {
            requireModuleEnabled(unitId);
            requireWrite(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            reservierungenService.processVehicleReservation(unitId, id, actor.getUserId(), body);
            return ActionResultDto.success("Fahrzeugreservierung wurde bearbeitet.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @PostMapping("/api/raeume/{id}/process")
    @ResponseBody
    public ActionResultDto processRoom(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long id,
            @RequestBody ProcessReservationRequest body) {
        try {
            requireModuleEnabled(unitId);
            requireWrite(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            reservierungenService.processRoomReservation(unitId, id, actor.getUserId(), body);
            return ActionResultDto.success("Raumreservierung wurde bearbeitet.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @GetMapping("/api/fahrzeuge/{id}/conflicts")
    @ResponseBody
    public Map<String, Object> vehicleConflicts(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long id) {
        requireModuleEnabled(unitId);
        requireWrite(actor, unitId);
        accessControlService.requireUnitAccess(actor, unitId);
        return Map.of("conflicts", reservierungenService.checkVehicleConflicts(unitId, id));
    }

    @GetMapping("/api/raeume/{id}/conflicts")
    @ResponseBody
    public Map<String, Object> roomConflicts(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long id) {
        requireModuleEnabled(unitId);
        requireWrite(actor, unitId);
        accessControlService.requireUnitAccess(actor, unitId);
        return Map.of("conflicts", reservierungenService.checkRoomConflicts(unitId, id));
    }

    @DeleteMapping("/api/fahrzeuge/{id}")
    @ResponseBody
    public ActionResultDto deleteVehicle(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long id) {
        try {
            requireModuleEnabled(unitId);
            requireWrite(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            reservierungenService.deleteVehicleReservation(unitId, id);
            return ActionResultDto.success("Reservierung gelöscht.");
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    @DeleteMapping("/api/raeume/{id}")
    @ResponseBody
    public ActionResultDto deleteRoom(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long id) {
        try {
            requireModuleEnabled(unitId);
            requireWrite(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            reservierungenService.deleteRoomReservation(unitId, id);
            return ActionResultDto.success("Reservierung gelöscht.");
        } catch (IllegalArgumentException e) {
            return ActionResultDto.failure(e.getMessage());
        }
    }

    private Unit resolveUnit(Long unitId, AppUserDetails actor, Model model) {
        Unit unit = unitService
                .resolveActiveUnit(unitId, actor)
                .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
        model.addAttribute("unitId", unit.getId());
        model.addAttribute("currentUnitName", unit.getName());
        return unit;
    }

    private void requireModuleEnabled(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.RESERVIERUNGEN, unitId)) {
            throw new IllegalArgumentException("Modul Reservierungen ist für diese Einheit nicht aktiv.");
        }
    }

    private void requireRead(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "reservierungen.read");
    }

    private void requireWrite(AppUserDetails actor, long unitId) {
        userPermissionService.requirePermission(actor, unitId, "reservierungen.write");
    }

    private boolean canWrite(AppUserDetails actor, long unitId) {
        return userPermissionService.hasPermission(actor, unitId, "reservierungen.write");
    }

    private static String redirectHome(Long unitId) {
        return unitId != null ? "redirect:/?unit=" + unitId : "redirect:/";
    }
}
