package de.feuerwehr.manager.web;

import de.feuerwehr.manager.reservierungen.ReservierungenSettingsService;
import de.feuerwehr.manager.reservierungen.UnitReservierungenSettings;
import de.feuerwehr.manager.divera.DiveraMappingService;
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
import java.util.Set;
import java.util.stream.Collectors;
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
    private final DiveraMappingService diveraMappingService;

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
            List<Vehicle> vehiclesTechnik = unitAdminService.listVehicles(unit.getId());
            List<Vehicle> vehiclesSorted = settingsService.sortVehicles(settings, vehiclesTechnik);
            List<Long> vehicleNotifyUsers = settingsService.vehicleNotificationUserIds(settings);
            List<String> vehicleNotifyEmails = settingsService.vehicleNotificationEmails(settings);
            List<Long> roomNotifyUsers = settingsService.roomNotificationUserIdsStored(settings);
            List<String> roomNotifyEmails = settingsService.roomNotificationEmailsStored(settings);
            model.addAttribute("unitId", unit.getId());
            model.addAttribute("currentUnitName", unit.getName());
            model.addAttribute("settingsTab", tab);
            model.addAttribute("settings", settings);
            model.addAttribute("unitUsers", users);
            model.addAttribute("vehicles", vehiclesSorted);
            model.addAttribute("vehiclesForSortModal", vehiclesSorted);
            model.addAttribute("selectedVehicleNotificationUserIds", vehicleNotifyUsers);
            model.addAttribute("selectedVehicleNotificationEmails", vehicleNotifyEmails);
            model.addAttribute(
                    "vehicleNotificationEmailsText", String.join("\n", vehicleNotifyEmails));
            model.addAttribute("selectedRoomNotificationUserIds", roomNotifyUsers);
            model.addAttribute("selectedRoomNotificationEmails", roomNotifyEmails);
            model.addAttribute("roomNotificationEmailsText", String.join("\n", roomNotifyEmails));
            List<Long> loeschIds = settingsService.loeschVehicleIds(settings);
            List<Long> vehicleCalendarIds = settingsService.vehicleGoogleCalendarAccountIds(settings);
            List<Long> roomCalendarIds = settingsService.roomGoogleCalendarAccountIds(settings);
            var googleCalendars = unitAdminService.listCalendarAccounts(unit.getId()).stream()
                    .filter(c -> c.isEnabled()
                            && c.getCalendarId() != null
                            && !c.getCalendarId().isBlank()
                            && ((c.getGoogleOauthRefreshToken() != null && !c.getGoogleOauthRefreshToken().isBlank())
                                    || (c.getServiceAccountJson() != null
                                            && !c.getServiceAccountJson().isBlank())))
                    .toList();
            vehicleCalendarIds = filterExistingCalendarAccountIds(vehicleCalendarIds, googleCalendars);
            roomCalendarIds = filterExistingCalendarAccountIds(roomCalendarIds, googleCalendars);
            model.addAttribute("selectedLoeschVehicleIds", loeschIds);
            model.addAttribute("loeschVehicleSummary", formatLoeschSummary(loeschIds.size()));
            model.addAttribute("googleCalendarAccounts", googleCalendars);
            model.addAttribute("selectedVehicleGoogleCalendarAccountIds", vehicleCalendarIds);
            model.addAttribute("selectedRoomGoogleCalendarAccountIds", roomCalendarIds);
            model.addAttribute(
                    "vehicleNotificationSummary",
                    formatNotificationSummary(vehicleNotifyUsers.size(), vehicleNotifyEmails.size(), false));
            model.addAttribute(
                    "roomNotificationSummary",
                    formatNotificationSummary(roomNotifyUsers.size(), roomNotifyEmails.size(), true));
            model.addAttribute("diveraRecipientGroups", diveraMappingService.listRecipientGroups(unit.getId()));
            return "settings/reservierungen";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return unitId != null ? "redirect:/admin?scope=einheit&tab=module&unit=" + unitId : "redirect:/settings";
        }
    }

    @PostMapping("/zugang")
    public String saveAccess(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "allowPublicReservation", defaultValue = "false") boolean allowPublicReservation,
            @RequestParam(name = "tab", defaultValue = "fahrzeug") String tab,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            settingsService.saveAccessSettings(unit, allowPublicReservation);
            redirectAttributes.addFlashAttribute("message", "Zugangseinstellung gespeichert.");
            return "redirect:/settings/reservierungen?unit=" + unit + "&tab=" + tab;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/reservierungen?unit=" + unit + "&tab=" + tab;
        }
    }

    @PostMapping("/fahrzeug")
    public String saveVehicle(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "vehicleSortMode", defaultValue = "manual") String vehicleSortMode,
            @RequestParam(name = "vehicleDiveraEnabled", defaultValue = "false") boolean vehicleDiveraEnabled,
            @RequestParam(name = "vehicleGoogleCalendarEnabled", defaultValue = "false")
                    boolean vehicleGoogleCalendarEnabled,
            @RequestParam(name = "vehicleGoogleCalendarAccountIds", required = false)
                    Long[] vehicleGoogleCalendarAccountIds,
            @RequestParam(name = "vehicleDiveraDefaultGroupId", required = false) String vehicleDiveraDefaultGroupId,
            @RequestParam(name = "vehicleDiveraGroupsJson", required = false) String vehicleDiveraGroupsJson,
            @RequestParam(name = "vehicleLoeschWarnEnabled", defaultValue = "false") boolean vehicleLoeschWarnEnabled,
            @RequestParam(name = "vehicleLoeschMinAvailable", defaultValue = "1") int vehicleLoeschMinAvailable,
            @RequestParam(name = "vehicleLoeschVehicleIds", required = false) Long[] vehicleLoeschVehicleIds,
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
                    vehicleGoogleCalendarAccountIds == null
                            ? List.of()
                            : Arrays.asList(vehicleGoogleCalendarAccountIds),
                    vehicleDiveraDefaultGroupId,
                    vehicleDiveraGroupsJson,
                    vehicleLoeschWarnEnabled,
                    vehicleLoeschMinAvailable,
                    vehicleLoeschVehicleIds == null ? List.of() : Arrays.asList(vehicleLoeschVehicleIds));
            redirectAttributes.addFlashAttribute("message", "Fahrzeug-Reservierungseinstellungen gespeichert.");
            return "redirect:/settings/reservierungen?unit=" + unit + "&tab=fahrzeug";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/reservierungen?unit=" + unit + "&tab=fahrzeug";
        }
    }

    @PostMapping("/fahrzeug/sortierung")
    public String saveVehicleSortOrder(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "vehicleIds", required = false) Long[] vehicleIds,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            settingsService.saveVehicleSortOrder(
                    unit, vehicleIds == null ? List.of() : Arrays.asList(vehicleIds));
            redirectAttributes.addFlashAttribute("message", "Fahrzeug-Reihenfolge gespeichert.");
            return "redirect:/settings/reservierungen?unit=" + unit + "&tab=fahrzeug";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/reservierungen?unit=" + unit + "&tab=fahrzeug";
        }
    }

    @PostMapping("/fahrzeug/benachrichtigungen")
    public String saveVehicleNotifications(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "notificationUserIds", required = false) Long[] notificationUserIds,
            @RequestParam(name = "notificationEmailsText", required = false) String notificationEmailsText,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            settingsService.saveVehicleNotifications(
                    unit,
                    notificationUserIds == null ? List.of() : Arrays.asList(notificationUserIds),
                    settingsService.parseEmailsFromText(notificationEmailsText));
            redirectAttributes.addFlashAttribute("message", "Fahrzeug-Benachrichtigungen gespeichert.");
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
            @RequestParam(name = "roomGoogleCalendarAccountIds", required = false) Long[] roomGoogleCalendarAccountIds,
            @RequestParam(name = "roomDiveraDefaultGroupId", required = false) String roomDiveraDefaultGroupId,
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
                    roomGoogleCalendarAccountIds == null
                            ? List.of()
                            : Arrays.asList(roomGoogleCalendarAccountIds),
                    roomDiveraDefaultGroupId);
            redirectAttributes.addFlashAttribute("message", "Raum-Reservierungseinstellungen gespeichert.");
            return "redirect:/settings/reservierungen?unit=" + unit + "&tab=raum";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/settings/reservierungen?unit=" + unit + "&tab=raum";
        }
    }

    @PostMapping("/raum/benachrichtigungen")
    public String saveRoomNotifications(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(name = "notificationUserIds", required = false) Long[] notificationUserIds,
            @RequestParam(name = "notificationEmailsText", required = false) String notificationEmailsText,
            RedirectAttributes redirectAttributes) {
        try {
            accessControlService.requireAdminLevel(actor);
            accessControlService.requireUnitAccess(actor, unit);
            requireModuleEnabled(unit);
            settingsService.saveRoomNotifications(
                    unit,
                    notificationUserIds == null ? List.of() : Arrays.asList(notificationUserIds),
                    settingsService.parseEmailsFromText(notificationEmailsText));
            redirectAttributes.addFlashAttribute("message", "Raum-Benachrichtigungen gespeichert.");
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

    private static String formatLoeschSummary(int count) {
        if (count <= 0) {
            return "Noch keine Löschfahrzeuge ausgewählt.";
        }
        return count == 1 ? "1 Löschfahrzeug ausgewählt." : count + " Löschfahrzeuge ausgewählt.";
    }

    private static String formatNotificationSummary(int users, int emails, boolean roomFallbackHint) {
        if (users == 0 && emails == 0) {
            return roomFallbackHint
                    ? "Keine eigenen Empfänger — es gelten die Fahrzeug-Benachrichtigungen."
                    : "Noch keine Empfänger ausgewählt.";
        }
        StringBuilder sb = new StringBuilder();
        if (users > 0) {
            sb.append(users).append(users == 1 ? " Benutzer" : " Benutzer");
        }
        if (emails > 0) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(emails).append(emails == 1 ? " E-Mail-Adresse" : " E-Mail-Adressen");
        }
        return sb.toString();
    }

    private static List<Long> filterExistingCalendarAccountIds(
            List<Long> selectedIds, List<de.feuerwehr.manager.unit.UnitCalendarAccount> available) {
        if (selectedIds == null || selectedIds.isEmpty() || available == null || available.isEmpty()) {
            return selectedIds != null ? selectedIds : List.of();
        }
        Set<Long> availableIds =
                available.stream().map(de.feuerwehr.manager.unit.UnitCalendarAccount::getId).collect(Collectors.toSet());
        return selectedIds.stream().filter(id -> id != null && availableIds.contains(id)).toList();
    }
}
