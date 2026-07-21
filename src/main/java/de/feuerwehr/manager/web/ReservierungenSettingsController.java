package de.feuerwehr.manager.web;

import de.feuerwehr.manager.reservierungen.ReservierungenSettingsService;
import de.feuerwehr.manager.reservierungen.UnitReservierungenSettings;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.technik.Vehicle;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitAdminService;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
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
@RequestMapping("/settings/reservierungen")
@RequiredArgsConstructor
public class ReservierungenSettingsController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final AccessControlService accessControlService;
    private final ReservierungenSettingsService settingsService;
    private final UnitAdminService unitAdminService;
    private final UserRepository userRepository;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "tab", defaultValue = "fahrzeug") String tab,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            Unit unit = unitService
                    .resolveActiveUnit(unitId, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
            accessControlService.requireUnitAccess(actor, unit.getId());
            requireModuleEnabled(unit.getId());
            UnitReservierungenSettings settings = settingsService.ensureSettings(unit.getId());
            List<User> users = userRepository.findUnitScopedAccountsByUnitId(unit.getId());
            List<Vehicle> vehicles = unitAdminService.listVehicles(unit.getId());
            model.addAttribute("unitId", unit.getId());
            model.addAttribute("currentUnitName", unit.getName());
            model.addAttribute("settingsTab", tab);
            model.addAttribute("settings", settings);
            model.addAttribute("unitUsers", users);
            model.addAttribute("vehicles", vehicles);
            model.addAttribute("selectedVehicleNotificationUserIds", settingsService.vehicleNotificationUserIds(settings));
            model.addAttribute("selectedRoomNotificationUserIds", settingsService.roomNotificationUserIds(settings));
            model.addAttribute("selectedLoeschVehicleIds", settingsService.loeschVehicleIds(settings));
            return "settings/reservierungen";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId != null ? "redirect:/admin?scope=einheit&tab=module&unit=" + unitId : "redirect:/settings";
        }
    }

    @PostMapping("/fahrzeug")
    public String saveVehicle(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "vehicleSortMode", defaultValue = "manual") String vehicleSortMode,
            @RequestParam(name = "vehicleDiveraEnabled", defaultValue = "false") boolean vehicleDiveraEnabled,
            @RequestParam(name = "vehicleGoogleCalendarEnabled", defaultValue = "false") boolean vehicleGoogleCalendarEnabled,
            @RequestParam(name = "vehicleDiveraDefaultGroupId", required = false) String vehicleDiveraDefaultGroupId,
            @RequestParam(name = "vehicleDiveraGroupsJson", required = false) String vehicleDiveraGroupsJson,
            @RequestParam(name = "vehicleLoeschWarnEnabled", defaultValue = "false") boolean vehicleLoeschWarnEnabled,
            @RequestParam(name = "vehicleLoeschMinAvailable", defaultValue = "1") int vehicleLoeschMinAvailable,
            @RequestParam(name = "vehicleLoeschVehicleIds", required = false) Long[] vehicleLoeschVehicleIds,
            @RequestParam(name = "vehicleNotificationUserIds", required = false) Long[] vehicleNotificationUserIds,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            settingsService.saveVehicleSettings(
                    unit,
                    vehicleSortMode,
                    vehicleDiveraEnabled,
                    vehicleGoogleCalendarEnabled,
                    vehicleDiveraDefaultGroupId,
                    vehicleDiveraGroupsJson,
                    vehicleLoeschWarnEnabled,
                    vehicleLoeschMinAvailable,
                    vehicleLoeschVehicleIds == null ? List.of() : Arrays.asList(vehicleLoeschVehicleIds),
                    vehicleNotificationUserIds == null ? List.of() : Arrays.asList(vehicleNotificationUserIds));
            redirectAttributes.addFlashAttribute("message", "Fahrzeug-Reservierungseinstellungen gespeichert.");
            return "redirect:/settings/reservierungen?unit=" + unit + "&tab=fahrzeug";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/reservierungen?unit=" + unit + "&tab=fahrzeug";
        }
    }

    @PostMapping("/raum")
    public String saveRoom(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "roomSortMode", defaultValue = "manual") String roomSortMode,
            @RequestParam(name = "roomDiveraEnabled", defaultValue = "false") boolean roomDiveraEnabled,
            @RequestParam(name = "roomGoogleCalendarEnabled", defaultValue = "false") boolean roomGoogleCalendarEnabled,
            @RequestParam(name = "roomDiveraDefaultGroupId", required = false) String roomDiveraDefaultGroupId,
            @RequestParam(name = "roomNotificationUserIds", required = false) Long[] roomNotificationUserIds,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            settingsService.saveRoomSettings(
                    unit,
                    roomSortMode,
                    roomDiveraEnabled,
                    roomGoogleCalendarEnabled,
                    roomDiveraDefaultGroupId,
                    roomNotificationUserIds == null ? List.of() : Arrays.asList(roomNotificationUserIds));
            redirectAttributes.addFlashAttribute("message", "Raum-Reservierungseinstellungen gespeichert.");
            return "redirect:/settings/reservierungen?unit=" + unit + "&tab=raum";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/reservierungen?unit=" + unit + "&tab=raum";
        }
    }

    private void requireModuleEnabled(long unitId) {
        if (!moduleSettingsService.isEnabled(AppModule.RESERVIERUNGEN, unitId)) {
            throw new IllegalArgumentException("Modul Reservierungen ist für diese Einheit nicht aktiv.");
        }
    }
}
