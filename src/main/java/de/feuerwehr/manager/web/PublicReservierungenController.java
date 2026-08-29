package de.feuerwehr.manager.web;

import de.feuerwehr.manager.reservierungen.CreateReservationRequest;
import de.feuerwehr.manager.reservierungen.LoeschfahrzeugWarningException;
import de.feuerwehr.manager.reservierungen.ReservationConflictException;
import de.feuerwehr.manager.reservierungen.ReservierungenConflictService;
import de.feuerwehr.manager.reservierungen.ReservierungenService;
import de.feuerwehr.manager.reservierungen.ReservierungenSettingsService;
import de.feuerwehr.manager.reservierungen.RoomReservation;
import de.feuerwehr.manager.reservierungen.VehicleReservation;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitAdminService;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.user.UserRepository;
import de.feuerwehr.manager.web.dto.ReservationActionResultDto;
import de.feuerwehr.manager.web.dto.ResourceOptionDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/reservieren")
@RequiredArgsConstructor
public class PublicReservierungenController {

    private final ReservierungenSettingsService settingsService;
    private final ReservierungenService reservierungenService;
    private final ReservierungenConflictService conflictService;
    private final UnitAdminService unitAdminService;
    private final PersonalService personalService;
    private final UserRepository userRepository;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model,
            RedirectAttributes redirectAttributes) {
        List<Unit> publicUnits = settingsService.listUnitsAllowingPublicReservation();
        model.addAttribute("publicReservation", true);
        model.addAttribute("publicUnits", publicUnits);
        if (publicUnits.isEmpty()) {
            return "reservieren/unavailable";
        }
        if (unitId == null) {
            if (publicUnits.size() == 1) {
                return "redirect:/reservieren?unit=" + publicUnits.get(0).getId();
            }
            return "reservieren/units";
        }
        Unit unit = publicUnits.stream()
                .filter(u -> u.getId().equals(unitId))
                .findFirst()
                .orElse(null);
        if (unit == null) {
            redirectAttributes.addFlashAttribute("error", "Für diese Einheit ist keine öffentliche Reservierung möglich.");
            return "redirect:/reservieren";
        }
        var vehicles = conflictService.listBookableVehicles(unit.getId());
        var rooms = unitAdminService.listRooms(unit.getId()).stream().filter(r -> r.isActive()).toList();
        model.addAttribute("unitId", unit.getId());
        model.addAttribute("currentUnitName", unit.getName());
        model.addAttribute("reservierungenBasePath", "/reservieren");
        model.addAttribute("canWrite", false);
        model.addAttribute("canManage", false);
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
        model.addAttribute("unitPersons", personalService.listSelectablePersons(unit.getId()));
        if (actor != null) {
            model.addAttribute("requesterName", requesterDisplayName(actor, unit.getId()));
            model.addAttribute(
                    "requesterEmail",
                    userRepository.findById(actor.getUserId()).map(u -> u.getLoginEmail()).orElse(""));
        } else {
            model.addAttribute("requesterName", "");
            model.addAttribute("requesterEmail", "");
        }
        return "reservierungen/index";
    }

    @PostMapping("/api/fahrzeuge")
    @ResponseBody
    public ReservationActionResultDto createVehicle(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestBody CreateReservationRequest body) {
        try {
            requirePublicAccess(unitId);
            List<VehicleReservation> created =
                    reservierungenService.createVehicleReservation(unitId, actorUserId(actor), body);
            String msg = created.size() == 1
                    ? "Fahrzeugreservierung wurde eingereicht. Sie erhalten eine Rückmeldung per E-Mail."
                    : created.size() + " Fahrzeugtermine wurden eingereicht. Sie erhalten eine Rückmeldung per E-Mail.";
            return ReservationActionResultDto.success(msg);
        } catch (LoeschfahrzeugWarningException e) {
            return ReservationActionResultDto.loeschWarning(e.getWarning());
        } catch (ReservationConflictException e) {
            return ReservationActionResultDto.conflicts(
                    e.getMessage(), e.getConflicts(), e.getConflictingResourceIds());
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
            requirePublicAccess(unitId);
            List<RoomReservation> created =
                    reservierungenService.createRoomReservation(unitId, actorUserId(actor), body);
            String msg = created.size() == 1
                    ? "Raumreservierung wurde eingereicht. Sie erhalten eine Rückmeldung per E-Mail."
                    : created.size() + " Raumtermine wurden eingereicht. Sie erhalten eine Rückmeldung per E-Mail.";
            return ReservationActionResultDto.success(msg);
        } catch (ReservationConflictException e) {
            return ReservationActionResultDto.conflicts(
                    e.getMessage(), e.getConflicts(), e.getConflictingResourceIds());
        } catch (IllegalArgumentException e) {
            return ReservationActionResultDto.failure(e.getMessage());
        }
    }

    private void requirePublicAccess(long unitId) {
        if (!settingsService.isPublicReservationOpen(unitId)) {
            throw new IllegalArgumentException("Reservierung ohne Anmeldung ist für diese Einheit nicht freigegeben.");
        }
    }

    private String requesterDisplayName(AppUserDetails actor, long unitId) {
        return personalService
                .findLinkedPerson(actor.getUserId(), unitId)
                .map(p -> p.anwesenheitDisplayName())
                .filter(name -> name != null && !name.isBlank())
                .orElse(actor.getDisplayName());
    }

    private static Long actorUserId(AppUserDetails actor) {
        return actor == null ? null : actor.getUserId();
    }
}
