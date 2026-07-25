package de.feuerwehr.manager.web;

import de.feuerwehr.manager.reservierungen.CreateReservationRequest;
import de.feuerwehr.manager.reservierungen.ImportReservationRequest;
import de.feuerwehr.manager.reservierungen.LoeschfahrzeugWarningException;
import de.feuerwehr.manager.reservierungen.ProcessReservationRequest;
import de.feuerwehr.manager.reservierungen.ReservationConflictException;
import de.feuerwehr.manager.reservierungen.ReservierungenConflictService;
import de.feuerwehr.manager.reservierungen.ReservierungenService;
import de.feuerwehr.manager.reservierungen.ReservierungenTab;
import de.feuerwehr.manager.reservierungen.RoomReservation;
import de.feuerwehr.manager.reservierungen.VehicleReservation;
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
import de.feuerwehr.manager.web.dto.ReservationActionResultDto;
import de.feuerwehr.manager.web.dto.ResourceOptionDto;
import java.util.List;
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
            var vehicles = conflictService.listBookableVehicles(unit.getId());
            var rooms = unitAdminService.listRooms(unit.getId()).stream().filter(r -> r.isActive()).toList();
            model.addAttribute("vehicles", vehicles);
            model.addAttribute("rooms", rooms);
            model.addAttribute(
                    "vehicleOptions",
                    vehicles.stream()
                            .map(v -> new ResourceOptionDto(v.getId(), v.getName() != null ? v.getName() : ""))
                            .toList());
            model.addAttribute(
                    "roomOptions",
                    rooms.stream()
                            .map(r -> new ResourceOptionDto(r.getId(), r.getName() != null ? r.getName() : ""))
                            .toList());
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
    public ReservationActionResultDto createVehicle(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestBody CreateReservationRequest body) {
        try {
            requireModuleEnabled(unitId);
            requireRead(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            List<VehicleReservation> created =
                    reservierungenService.createVehicleReservation(unitId, actor.getUserId(), body);
            String msg = created.size() == 1
                    ? "Fahrzeugreservierung wurde eingereicht."
                    : created.size() + " Fahrzeugtermine wurden eingereicht.";
            return ReservationActionResultDto.success(msg);
        } catch (LoeschfahrzeugWarningException e) {
            return ReservationActionResultDto.loeschWarning(e.getWarning());
        } catch (ReservationConflictException e) {
            return ReservationActionResultDto.conflicts(e.getMessage(), e.getConflicts());
        } catch (IllegalArgumentException e) {
            return ReservationActionResultDto.failure(e.getMessage());
        }
    }

    @PostMapping("/api/raeume")
    @ResponseBody
    public ReservationActionResultDto createRoom(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestBody CreateReservationRequest body) {
        try {
            requireModuleEnabled(unitId);
            requireRead(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            List<RoomReservation> created =
                    reservierungenService.createRoomReservation(unitId, actor.getUserId(), body);
            String msg = created.size() == 1
                    ? "Raumreservierung wurde eingereicht."
                    : created.size() + " Raumtermine wurden eingereicht.";
            return ReservationActionResultDto.success(msg);
        } catch (ReservationConflictException e) {
            return ReservationActionResultDto.conflicts(e.getMessage(), e.getConflicts());
        } catch (IllegalArgumentException e) {
            return ReservationActionResultDto.failure(e.getMessage());
        }
    }

    @PostMapping("/api/import")
    @ResponseBody
    public ReservationActionResultDto importApproved(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestBody ImportReservationRequest body) {
        try {
            requireModuleEnabled(unitId);
            requireWrite(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            List<String> notes = reservierungenService.importApprovedReservation(unitId, actor.getUserId(), body);
            return ReservationActionResultDto.success("Reservierung wurde als genehmigt übernommen.", notes);
        } catch (IllegalArgumentException e) {
            return ReservationActionResultDto.failure(e.getMessage());
        }
    }

    @PostMapping("/api/fahrzeuge/{id}/process")
    @ResponseBody
    public ReservationActionResultDto processVehicle(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long id,
            @RequestBody ProcessReservationRequest body) {
        try {
            requireModuleEnabled(unitId);
            requireWrite(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            List<String> notes =
                    reservierungenService.processVehicleReservation(unitId, id, actor.getUserId(), body);
            return ReservationActionResultDto.success("Fahrzeugreservierung wurde bearbeitet.", notes);
        } catch (ReservationConflictException e) {
            return ReservationActionResultDto.conflicts(e.getMessage(), e.getConflicts());
        } catch (LoeschfahrzeugWarningException e) {
            return ReservationActionResultDto.loeschWarning(e.getWarning());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ReservationActionResultDto.failure(e.getMessage());
        }
    }

    @PostMapping("/api/raeume/{id}/process")
    @ResponseBody
    public ReservationActionResultDto processRoom(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @PathVariable long id,
            @RequestBody ProcessReservationRequest body) {
        try {
            requireModuleEnabled(unitId);
            requireWrite(actor, unitId);
            accessControlService.requireUnitAccess(actor, unitId);
            List<String> notes =
                    reservierungenService.processRoomReservation(unitId, id, actor.getUserId(), body);
            return ReservationActionResultDto.success("Raumreservierung wurde bearbeitet.", notes);
        } catch (ReservationConflictException e) {
            return ReservationActionResultDto.conflicts(e.getMessage(), e.getConflicts());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ReservationActionResultDto.failure(e.getMessage());
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
            return ActionResultDto.success(
                    "Reservierung gelöscht. Termin in DIVERA/Google (falls vorhanden) entfernt; Antragsteller benachrichtigt.");
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
            return ActionResultDto.success(
                    "Reservierung gelöscht. Termin in DIVERA/Google (falls vorhanden) entfernt; Antragsteller benachrichtigt.");
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
