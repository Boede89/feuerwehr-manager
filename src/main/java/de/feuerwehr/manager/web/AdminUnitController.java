package de.feuerwehr.manager.web;

import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitAdminService;
import de.feuerwehr.manager.unit.UnitDiveraSettings;
import de.feuerwehr.manager.unit.UnitDiveraSettingsRepository;
import de.feuerwehr.manager.unit.UnitRolePermission;
import de.feuerwehr.manager.unit.UnitRoleService;
import de.feuerwehr.manager.unit.UnitRoleType;
import de.feuerwehr.manager.unit.UnitService;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/unit")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'UNIT_ADMIN')")
@RequiredArgsConstructor
public class AdminUnitController {

    private final UnitService unitService;
    private final UnitAdminService unitAdminService;
    private final UnitRoleService unitRoleService;
    private final UnitDiveraSettingsRepository diveraSettingsRepository;
    private final PersonalService personalService;

    @PostMapping("/config")
    public String saveConfig(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String name,
            @RequestParam(required = false) String street,
            @RequestParam(required = false) String postalCity,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "konfiguration", () -> {
            unitAdminService.saveStammdaten(unit, name, street, postalCity);
            redirectAttributes.addFlashAttribute("message", "Stammdaten gespeichert.");
        });
    }

    @PostMapping("/logo")
    public String uploadLogo(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam("logoFile") MultipartFile logoFile,
            RedirectAttributes redirectAttributes) {
        try {
            if (logoFile.isEmpty()) {
                throw new IllegalArgumentException("Bitte eine Bilddatei auswählen.");
            }
            String contentType = logoFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IllegalArgumentException("Nur Bilddateien (PNG, JPG, WebP) sind erlaubt.");
            }
            byte[] imageBytes = logoFile.getBytes();
            return withUnit(actor, unit, redirectAttributes, "konfiguration", () -> {
                unitAdminService.saveLogo(unit, contentType, imageBytes);
                redirectAttributes.addFlashAttribute("message", "Wappen wurde gespeichert.");
            });
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Wappen konnte nicht gelesen werden.");
            return "redirect:/admin?scope=einheit&tab=konfiguration&unit=" + unit;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin?scope=einheit&tab=konfiguration&unit=" + unit;
        }
    }

    @PostMapping("/logo/delete")
    public String deleteLogo(
            @AuthenticationPrincipal AppUserDetails actor, @RequestParam long unit, RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "konfiguration", () -> {
            unitAdminService.clearLogo(unit);
            redirectAttributes.addFlashAttribute("message", "Wappen entfernt.");
        });
    }

    @PostMapping("/roles")
    public String createRole(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String name,
            @RequestParam(defaultValue = "DIENSTGRAD") UnitRoleType roleType,
            @RequestParam(required = false) String[] permissions,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "rollen", () -> {
            unitRoleService.create(unit, name, roleType, permissionList(permissions), null);
            redirectAttributes.addFlashAttribute("message", "Rolle angelegt.");
        });
    }

    @PostMapping("/roles/update")
    public String updateRole(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long roleId,
            @RequestParam String name,
            @RequestParam(defaultValue = "DIENSTGRAD") UnitRoleType roleType,
            @RequestParam(required = false) String[] permissions,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "rollen", () -> {
            unitRoleService.update(unit, roleId, name, roleType, permissionList(permissions), null);
            redirectAttributes.addFlashAttribute("message", "Rolle gespeichert.");
        });
    }

    @PostMapping("/roles/delete")
    public String deleteRole(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long roleId,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "rollen", () -> {
            unitRoleService.delete(unit, roleId);
            redirectAttributes.addFlashAttribute("message", "Rolle gelöscht.");
        });
    }

    @PostMapping("/smtp")
    public String saveSmtp(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(required = false) String smtpHost,
            @RequestParam(required = false) Integer smtpPort,
            @RequestParam(required = false) String smtpUsername,
            @RequestParam(required = false) String smtpPassword,
            @RequestParam(required = false) String smtpFromEmail,
            @RequestParam(required = false) String smtpFromName,
            @RequestParam(required = false) String smtpEncryption,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "schnittstellen", () -> {
            unitAdminService.saveSmtp(
                    unit, smtpHost, smtpPort, smtpUsername, smtpPassword, smtpFromEmail, smtpFromName, smtpEncryption);
            redirectAttributes.addFlashAttribute("message", "SMTP-Einstellungen gespeichert.");
        });
    }

    @PostMapping("/calendar")
    public String saveCalendar(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(required = false) String calendarUrl,
            @RequestParam(required = false) String calendarId,
            @RequestParam(required = false) String serviceAccountJson,
            @RequestParam(required = false) String enabled,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "schnittstellen", () -> {
            boolean on = "true".equalsIgnoreCase(enabled) || "on".equalsIgnoreCase(enabled);
            unitAdminService.saveCalendar(unit, calendarUrl, calendarId, serviceAccountJson, on);
            redirectAttributes.addFlashAttribute("message", "Kalender-Einstellungen gespeichert.");
        });
    }

    @PostMapping("/divera")
    public String saveDivera(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(required = false) String accessKey,
            @RequestParam(required = false) String webhookSecret,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "schnittstellen", () -> {
            UnitDiveraSettings settings = diveraSettingsRepository
                    .findByUnitId(unit)
                    .orElseThrow(() -> new IllegalArgumentException("Keine Divera-Einstellungen für diese Einheit."));
            settings.setApiBaseUrl("https://app.divera247.com");
            if (accessKey != null && !accessKey.isBlank()) {
                settings.setAccessKey(accessKey.trim().replaceAll("[\\r\\n\\t\\v]+", ""));
            }
            if (webhookSecret != null && !webhookSecret.isBlank()) {
                settings.setWebhookSecret(webhookSecret.trim());
            }
            diveraSettingsRepository.save(settings);
            redirectAttributes.addFlashAttribute("message", "Divera-Einstellungen gespeichert.");
        });
    }

    @PostMapping("/vehicles")
    public String createVehicle(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "technik", () -> {
            unitAdminService.createVehicle(unit, name, description);
            redirectAttributes.addFlashAttribute("message", "Fahrzeug angelegt.");
        });
    }

    @PostMapping("/vehicles/update")
    public String updateVehicle(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long vehicleId,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String active,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "technik", () -> {
            unitAdminService.updateVehicle(unit, vehicleId, name, description, "true".equalsIgnoreCase(active));
            redirectAttributes.addFlashAttribute("message", "Fahrzeug gespeichert.");
        });
    }

    @PostMapping("/vehicles/delete")
    public String deleteVehicle(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long vehicleId,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "technik", () -> {
            unitAdminService.deleteVehicle(unit, vehicleId);
            redirectAttributes.addFlashAttribute("message", "Fahrzeug gelöscht.");
        });
    }

    @PostMapping("/rooms")
    public String createRoom(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "technik", () -> {
            unitAdminService.createRoom(unit, name, description);
            redirectAttributes.addFlashAttribute("message", "Raum angelegt.");
        });
    }

    @PostMapping("/rooms/update")
    public String updateRoom(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long roomId,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String active,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "technik", () -> {
            unitAdminService.updateRoom(unit, roomId, name, description, "true".equalsIgnoreCase(active));
            redirectAttributes.addFlashAttribute("message", "Raum gespeichert.");
        });
    }

    @PostMapping("/rooms/delete")
    public String deleteRoom(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long roomId,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "technik", () -> {
            unitAdminService.deleteRoom(unit, roomId);
            redirectAttributes.addFlashAttribute("message", "Raum gelöscht.");
        });
    }

    @PostMapping("/equipment")
    public String createEquipment(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long vehicleId,
            @RequestParam String name,
            @RequestParam(required = false) Long categoryId,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "technik",
                () -> {
                    unitAdminService.createEquipment(unit, vehicleId, name, categoryId);
                    redirectAttributes.addFlashAttribute("message", "Gerät registriert.");
                },
                "vehicle=" + vehicleId);
    }

    @PostMapping("/equipment/delete")
    public String deleteEquipment(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long vehicleId,
            @RequestParam long equipmentId,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "technik",
                () -> {
                    unitAdminService.deleteEquipment(unit, equipmentId);
                    redirectAttributes.addFlashAttribute("message", "Gerät entfernt.");
                },
                "vehicle=" + vehicleId);
    }

    @PostMapping("/qualifications")
    public String createQualification(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String name,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "ausbildung", () -> {
            personalService.createQualificationType(unit, name);
            redirectAttributes.addFlashAttribute("message", "Qualifikation angelegt.");
        });
    }

    @PostMapping("/courses")
    public String createCourse(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String name,
            @RequestParam(required = false) Long qualificationTypeId,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "ausbildung", () -> {
            personalService.createCourse(unit, name, qualificationTypeId);
            redirectAttributes.addFlashAttribute("message", "Lehrgang angelegt.");
        });
    }

    private String withUnit(
            AppUserDetails actor,
            long unitId,
            RedirectAttributes redirectAttributes,
            String tab,
            Runnable action) {
        return withUnit(actor, unitId, redirectAttributes, tab, action, null);
    }

    private String withUnit(
            AppUserDetails actor,
            long unitId,
            RedirectAttributes redirectAttributes,
            String tab,
            Runnable action,
            String extraQuery) {
        try {
            unitService
                    .resolveActiveUnit(unitId, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
            action.run();
            redirectAttributes.addFlashAttribute("saved", true);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Aktion fehlgeschlagen: " + e.getMessage());
        }
        String url = "redirect:/admin?scope=einheit&tab=" + tab + "&unit=" + unitId;
        if (extraQuery != null && !extraQuery.isBlank()) {
            url += "&" + extraQuery;
        }
        return url;
    }

    private static List<String> permissionList(String[] permissions) {
        if (permissions == null) {
            return List.of();
        }
        return UnitRolePermission.filterAllowed(Arrays.asList(permissions));
    }
}
