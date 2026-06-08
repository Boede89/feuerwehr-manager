package de.feuerwehr.manager.web;

import de.feuerwehr.manager.divera.DiveraMappingService;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.technik.UnitVehicleTypeService;
import de.feuerwehr.manager.technik.VehicleChecklistService;
import de.feuerwehr.manager.technik.VehicleFormData;
import java.math.BigDecimal;
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
    private final UnitVehicleTypeService unitVehicleTypeService;
    private final VehicleChecklistService vehicleChecklistService;
    private final DiveraMappingService diveraMappingService;

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

    @PostMapping("/roles/move")
    public String moveRole(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long roleId,
            @RequestParam String direction,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "rollen", () -> {
            unitRoleService.moveRole(unit, roleId, direction);
        });
    }

    @PostMapping("/smtp")
    public String saveSmtp(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(required = false) Long smtpAccountId,
            @RequestParam String label,
            @RequestParam(required = false) String smtpHost,
            @RequestParam(required = false) Integer smtpPort,
            @RequestParam(required = false) String smtpUsername,
            @RequestParam(required = false) String smtpPassword,
            @RequestParam(required = false) String smtpFromEmail,
            @RequestParam(required = false) String smtpFromName,
            @RequestParam(required = false) String smtpEncryption,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "schnittstellen", () -> {
            if (smtpAccountId != null && smtpAccountId > 0) {
                unitAdminService.updateSmtpAccount(
                        unit,
                        smtpAccountId,
                        label,
                        smtpHost,
                        smtpPort,
                        smtpUsername,
                        smtpPassword,
                        smtpFromEmail,
                        smtpFromName,
                        smtpEncryption);
                redirectAttributes.addFlashAttribute("message", "SMTP-Konto gespeichert.");
            } else {
                unitAdminService.createSmtpAccount(
                        unit,
                        label,
                        smtpHost,
                        smtpPort,
                        smtpUsername,
                        smtpPassword,
                        smtpFromEmail,
                        smtpFromName,
                        smtpEncryption);
                redirectAttributes.addFlashAttribute("message", "SMTP-Konto angelegt.");
            }
        });
    }

    @PostMapping("/smtp/delete")
    public String deleteSmtp(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long smtpAccountId,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "schnittstellen", () -> {
            unitAdminService.deleteSmtpAccount(unit, smtpAccountId);
            redirectAttributes.addFlashAttribute("message", "SMTP-Konto gelöscht.");
        });
    }

    @PostMapping("/calendar")
    public String saveCalendar(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam(required = false) Long calendarAccountId,
            @RequestParam String label,
            @RequestParam(required = false) String calendarUrl,
            @RequestParam(required = false) String calendarId,
            @RequestParam(required = false) String serviceAccountJson,
            @RequestParam(required = false) String enabled,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "schnittstellen", () -> {
            boolean on = "true".equalsIgnoreCase(enabled) || "on".equalsIgnoreCase(enabled);
            if (calendarAccountId != null && calendarAccountId > 0) {
                unitAdminService.updateCalendarAccount(
                        unit, calendarAccountId, label, calendarUrl, calendarId, serviceAccountJson, on);
                redirectAttributes.addFlashAttribute("message", "Kalender gespeichert.");
            } else {
                unitAdminService.createCalendarAccount(
                        unit, label, calendarUrl, calendarId, serviceAccountJson, on);
                redirectAttributes.addFlashAttribute("message", "Kalender angelegt.");
            }
        });
    }

    @PostMapping("/calendar/delete")
    public String deleteCalendar(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long calendarAccountId,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "schnittstellen", () -> {
            unitAdminService.deleteCalendarAccount(unit, calendarAccountId);
            redirectAttributes.addFlashAttribute("message", "Kalender gelöscht.");
        });
    }

    @PostMapping("/divera/recipient-groups")
    public String createDiveraRecipientGroup(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String groupId,
            @RequestParam String label,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "schnittstellen", () -> {
            diveraMappingService.createRecipientGroup(unit, groupId, label);
            redirectAttributes.addFlashAttribute("message", "Empfänger-Gruppe gespeichert.");
        }, "openModal=divera-recipient-groups");
    }

    @PostMapping("/divera/recipient-groups/delete")
    public String deleteDiveraRecipientGroup(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long recipientGroupId,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "schnittstellen", () -> {
            diveraMappingService.deleteRecipientGroup(unit, recipientGroupId);
            redirectAttributes.addFlashAttribute("message", "Empfänger-Gruppe gelöscht.");
        }, "openModal=divera-recipient-groups");
    }

    @PostMapping("/divera/status-ids")
    public String createDiveraStatusId(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String statusId,
            @RequestParam String label,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "schnittstellen", () -> {
            diveraMappingService.createStatusId(unit, statusId, label);
            redirectAttributes.addFlashAttribute("message", "Status-ID gespeichert.");
        }, "openModal=divera-status-ids");
    }

    @PostMapping("/divera/status-ids/delete")
    public String deleteDiveraStatusId(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long diveraStatusRowId,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "schnittstellen", () -> {
            diveraMappingService.deleteStatusId(unit, diveraStatusRowId);
            redirectAttributes.addFlashAttribute("message", "Status-ID gelöscht.");
        }, "openModal=divera-status-ids");
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
            @RequestParam(required = false) String vehicleType,
            @RequestParam(required = false) String licensePlate,
            @RequestParam(required = false) Integer yearBuilt,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String lengthM,
            @RequestParam(required = false) String widthM,
            @RequestParam(required = false) String heightM,
            @RequestParam(required = false) Integer weightKg,
            @RequestParam(required = false) String serviceStatus,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {
        try {
            unitService
                    .resolveActiveUnit(unit, actor)
                    .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit."));
            long newId = unitAdminService
                    .createVehicle(
                            unit,
                            toVehicleForm(
                                    name,
                                    description,
                                    vehicleType,
                                    licensePlate,
                                    yearBuilt,
                                    phone,
                                    lengthM,
                                    widthM,
                                    heightM,
                                    weightKg,
                                    serviceStatus,
                                    notes))
                    .getId();
            redirectAttributes.addFlashAttribute("message", "Fahrzeug angelegt.");
            return "redirect:/admin?scope=einheit&tab=technik&unit=" + unit + "&vehicle=" + newId;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin?scope=einheit&tab=technik&unit=" + unit;
        }
    }

    @PostMapping("/vehicles/update")
    public String updateVehicle(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long vehicleId,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String vehicleType,
            @RequestParam(required = false) String licensePlate,
            @RequestParam(required = false) Integer yearBuilt,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String lengthM,
            @RequestParam(required = false) String widthM,
            @RequestParam(required = false) String heightM,
            @RequestParam(required = false) Integer weightKg,
            @RequestParam(required = false) String serviceStatus,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "technik",
                () -> {
                    unitAdminService.updateVehicle(
                            unit,
                            vehicleId,
                            toVehicleForm(
                                    name,
                                    description,
                                    vehicleType,
                                    licensePlate,
                                    yearBuilt,
                                    phone,
                                    lengthM,
                                    widthM,
                                    heightM,
                                    weightKg,
                                    serviceStatus,
                                    notes));
                    redirectAttributes.addFlashAttribute("message", "Fahrzeug gespeichert.");
                },
                "vehicle=" + vehicleId);
    }

    @PostMapping("/vehicles/move")
    public String moveVehicle(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long vehicleId,
            @RequestParam String direction,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "technik", () -> {
            unitAdminService.moveVehicle(unit, vehicleId, direction);
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

    @PostMapping("/vehicle-types")
    public String createVehicleType(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String typeKey,
            @RequestParam String label,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "technik",
                () -> {
                    unitVehicleTypeService.create(unit, typeKey, label);
                    redirectAttributes.addFlashAttribute("message", "Fahrzeugtyp hinzugefügt.");
                },
                "openModal=vehicle-types");
    }

    @PostMapping("/vehicle-types/delete")
    public String deleteVehicleType(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long typeId,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "technik",
                () -> {
                    unitVehicleTypeService.delete(unit, typeId);
                    redirectAttributes.addFlashAttribute("message", "Fahrzeugtyp gelöscht.");
                },
                "openModal=vehicle-types");
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

    @PostMapping("/equipment/categories")
    public String createEquipmentCategory(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long vehicleId,
            @RequestParam String name,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "technik",
                () -> {
                    unitAdminService.createEquipmentCategory(unit, vehicleId, name);
                    redirectAttributes.addFlashAttribute("message", "Kategorie angelegt.");
                },
                "vehicle=" + vehicleId + "&vt=geraete&openModal=equipment-categories");
    }

    @PostMapping("/equipment/categories/delete")
    public String deleteEquipmentCategory(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long vehicleId,
            @RequestParam long categoryId,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "technik",
                () -> {
                    unitAdminService.deleteEquipmentCategory(unit, vehicleId, categoryId);
                    redirectAttributes.addFlashAttribute("message", "Kategorie entfernt.");
                },
                "vehicle=" + vehicleId + "&vt=geraete&openModal=equipment-categories");
    }

    @PostMapping("/equipment/update")
    public String updateEquipment(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long vehicleId,
            @RequestParam long equipmentId,
            @RequestParam String name,
            @RequestParam(required = false) Long categoryId,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "technik",
                () -> {
                    unitAdminService.updateEquipment(unit, vehicleId, equipmentId, name, categoryId);
                    redirectAttributes.addFlashAttribute("message", "Gerät gespeichert.");
                },
                "vehicle=" + vehicleId + "&vt=geraete");
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
                "vehicle=" + vehicleId + "&vt=geraete");
    }

    @PostMapping("/checklists/templates")
    public String createChecklistTemplate(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long vehicleId,
            @RequestParam String name,
            @RequestParam(required = false, defaultValue = "manuell") String interval,
            @RequestParam(name = "itemLabel", required = false) List<String> itemLabels,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "technik",
                () -> {
                    vehicleChecklistService.createTemplate(
                            unit, vehicleId, name, interval, itemLabels, actor.getUserId());
                    redirectAttributes.addFlashAttribute("message", "Vorlage gespeichert.");
                },
                "vehicle=" + vehicleId + "&vt=checklisten");
    }

    @PostMapping("/checklists/templates/delete")
    public String deleteChecklistTemplate(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long vehicleId,
            @RequestParam long templateId,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "technik",
                () -> {
                    vehicleChecklistService.deleteTemplate(unit, vehicleId, templateId);
                    redirectAttributes.addFlashAttribute("message", "Vorlage gelöscht.");
                },
                "vehicle=" + vehicleId + "&vt=checklisten");
    }

    @PostMapping("/checklists")
    public String createChecklist(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long vehicleId,
            @RequestParam long templateId,
            @RequestParam(required = false) String notes,
            @RequestParam(name = "itemId", required = false) List<Long> itemIds,
            @RequestParam(name = "result", required = false) List<String> results,
            @RequestParam(name = "itemNote", required = false) List<String> itemNotes,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "technik",
                () -> {
                    vehicleChecklistService.createChecklist(
                            unit,
                            vehicleId,
                            templateId,
                            notes,
                            itemIds,
                            results,
                            itemNotes,
                            actor.getUserId(),
                            actor.getDisplayName());
                    redirectAttributes.addFlashAttribute("message", "Checkliste gespeichert.");
                },
                "vehicle=" + vehicleId + "&vt=checklisten");
    }

    @PostMapping("/checklists/delete")
    public String deleteChecklist(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long vehicleId,
            @RequestParam long checklistId,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "technik",
                () -> {
                    vehicleChecklistService.deleteChecklist(unit, vehicleId, checklistId);
                    redirectAttributes.addFlashAttribute("message", "Checkliste gelöscht.");
                },
                "vehicle=" + vehicleId + "&vt=checklisten");
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
                "vehicle=" + vehicleId + "&vt=geraete");
    }

    @PostMapping("/qualifications")
    public String createQualification(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String name,
            @RequestParam(name = "dienstgradRoleId", required = false) Long dienstgradRoleId,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "ausbildung",
                () -> {
                    personalService.createQualificationType(unit, name, dienstgradRoleId);
                    redirectAttributes.addFlashAttribute("message", "Qualifikation angelegt.");
                },
                "openModal=qualification-new");
    }

    @PostMapping("/qualifications/update")
    public String updateQualification(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long qualificationTypeId,
            @RequestParam String name,
            @RequestParam(required = false, defaultValue = "false") boolean active,
            @RequestParam(name = "dienstgradRoleId", required = false) Long dienstgradRoleId,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "ausbildung",
                () -> {
                    personalService.updateQualificationType(
                            unit, qualificationTypeId, name, active, dienstgradRoleId);
                    redirectAttributes.addFlashAttribute("message", "Qualifikation gespeichert.");
                });
    }

    @PostMapping("/qualifications/move")
    public String moveQualification(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long qualificationTypeId,
            @RequestParam String direction,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "ausbildung", () -> {
            personalService.moveQualificationType(unit, qualificationTypeId, direction);
        });
    }

    @PostMapping("/qualifications/delete")
    public String deleteQualification(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long qualificationTypeId,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "ausbildung",
                () -> {
                    personalService.deleteQualificationType(unit, qualificationTypeId);
                    redirectAttributes.addFlashAttribute("message", "Qualifikation gelöscht.");
                });
    }

    @PostMapping("/courses")
    public String createCourse(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam String name,
            @RequestParam(required = false) Long qualificationTypeId,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "ausbildung",
                () -> {
                    personalService.createCourse(unit, name, qualificationTypeId);
                    redirectAttributes.addFlashAttribute("message", "Lehrgang angelegt.");
                },
                "openModal=course-new");
    }

    @PostMapping("/courses/update")
    public String updateCourse(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long courseId,
            @RequestParam String name,
            @RequestParam(required = false) Long qualificationTypeId,
            @RequestParam(required = false, defaultValue = "false") boolean active,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "ausbildung",
                () -> {
                    personalService.updateCourse(unit, courseId, name, qualificationTypeId, active);
                    redirectAttributes.addFlashAttribute("message", "Lehrgang gespeichert.");
                });
    }

    @PostMapping("/courses/move")
    public String moveCourse(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long courseId,
            @RequestParam String direction,
            RedirectAttributes redirectAttributes) {
        return withUnit(actor, unit, redirectAttributes, "ausbildung", () -> {
            personalService.moveCourse(unit, courseId, direction);
        });
    }

    @PostMapping("/courses/delete")
    public String deleteCourse(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam long unit,
            @RequestParam long courseId,
            RedirectAttributes redirectAttributes) {
        return withUnit(
                actor,
                unit,
                redirectAttributes,
                "ausbildung",
                () -> {
                    personalService.deleteCourse(unit, courseId);
                    redirectAttributes.addFlashAttribute("message", "Lehrgang gelöscht.");
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

    private static VehicleFormData toVehicleForm(
            String name,
            String description,
            String vehicleType,
            String licensePlate,
            Integer yearBuilt,
            String phone,
            String lengthM,
            String widthM,
            String heightM,
            Integer weightKg,
            String serviceStatus,
            String notes) {
        return new VehicleFormData(
                name,
                description,
                vehicleType,
                licensePlate,
                yearBuilt,
                phone,
                parseDecimal(lengthM),
                parseDecimal(widthM),
                parseDecimal(heightM),
                weightKg,
                serviceStatus,
                notes);
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value.trim().replace(',', '.'));
    }

    private static List<String> permissionList(String[] permissions) {
        if (permissions == null) {
            return List.of();
        }
        return UnitRolePermission.filterAllowed(Arrays.asList(permissions));
    }
}
