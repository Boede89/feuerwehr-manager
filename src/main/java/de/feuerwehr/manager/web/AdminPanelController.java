package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.unit.UnitRoleService;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.mail.AccountMailService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserManagementService;
import de.feuerwehr.manager.user.UserRfidCard;
import de.feuerwehr.manager.user.UserRole;
import de.feuerwehr.manager.user.UserRoleLabels;
import de.feuerwehr.manager.user.UserService;
import de.feuerwehr.manager.web.dto.UserDataExport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'UNIT_ADMIN')")
@RequiredArgsConstructor
public class AdminPanelController {

    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final TestModeService testModeService;
    private final UserManagementService userManagementService;
    private final AdminGlobalViewService adminGlobalViewService;
    private final AdminUnitViewService adminUnitViewService;
    private final ObjectMapper objectMapper;
    private final AccountMailService accountMailService;
    private final UserService userService;
    private final UnitRoleService unitRoleService;
    private final AccessControlService accessControlService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "scope", defaultValue = "einheit") String scope,
            @RequestParam(name = "tab", required = false) String tab,
            @RequestParam(name = "vehicle", required = false) Long selectedVehicleId,
            @RequestParam(name = "vt", required = false) String vehicleSubTab,
            Model model) {
        boolean superAdmin = actor.getRole().isSuperAdmin();
        if (!superAdmin) {
            scope = "einheit";
        } else if (!"global".equals(scope)) {
            scope = "einheit";
        }

        if ("global".equals(scope)) {
            tab = normalizeGlobalTab(tab != null ? tab : "konfiguration");
        } else {
            tab = normalizeUnitTab(tab != null ? tab : "konfiguration");
        }

        model.addAttribute("adminScope", scope);
        model.addAttribute("adminTab", tab);
        model.addAttribute("showGlobalScope", superAdmin);
        model.addAttribute("testModeEnabled", testModeService.isEnabled());

        if ("global".equals(scope)) {
            populateGlobalUserFormModel(model);
            switch (tab) {
                case "benutzer" ->
                        populateAdminUsersTab(model, userManagementService.listAdminLevelAccounts(), actor);
                case "konfiguration" -> adminGlobalViewService.populateKonfiguration(model);
                case "einheiten" -> adminGlobalViewService.populateEinheiten(model);
                case "schnittstellen" -> adminGlobalViewService.populateSmtp(model);
                case "audit" -> adminGlobalViewService.populateAuditLog(model);
                case "container-log" -> adminGlobalViewService.populateContainerLog(model);
                default -> {}
            }
            return "admin/index";
        }

        populateUserFormModel(actor, model, null);

        if (unitService.findActiveOrdered(actor).isEmpty()) {
            model.addAttribute("noUnit", true);
            return "admin/index";
        }

        Optional<Unit> unit = unitService.resolveActiveUnit(unitId, actor);
        if (unit.isEmpty()) {
            return "redirect:/admin?scope=einheit&tab=" + tab;
        }

        Unit active = unit.get();
        long resolvedId = active.getId();
        if (unitId == null || !unitId.equals(resolvedId)) {
            return "redirect:/admin?scope=einheit&tab=" + tab + "&unit=" + resolvedId;
        }

        model.addAttribute("unitId", resolvedId);
        model.addAttribute("currentUnitName", active.getName());
        model.addAttribute("unit", active);
        populateUserFormModel(actor, model, resolvedId);

        switch (tab) {
            case "konfiguration" -> adminUnitViewService.populateKonfiguration(model, active);
            case "rollen" -> adminUnitViewService.populateRollen(model, resolvedId);
            case "schnittstellen" -> adminUnitViewService.populateSchnittstellen(model, resolvedId);
            case "module" -> adminUnitViewService.populateModule(model, resolvedId);
            case "technik" -> {
                if (selectedVehicleId != null) {
                    model.addAttribute("selectedVehicleId", selectedVehicleId);
                }
                model.addAttribute("vehicleSubTab", normalizeVehicleSubTab(vehicleSubTab));
                adminUnitViewService.populateTechnik(model, resolvedId);
            }
            case "ausbildung" -> adminUnitViewService.populateAusbildung(model, resolvedId);
            case "benutzer" -> populateUnitUsersTab(model, actor, resolvedId);
            default -> adminUnitViewService.populateKonfiguration(model, active);
        }

        return "admin/index";
    }

    @PostMapping("/modules")
    public String saveModules(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestParam Map<String, String> params,
            RedirectAttributes redirectAttributes) {
        long resolvedUnitId = unitService
                .resolveActiveUnit(unitId, actor)
                .map(Unit::getId)
                .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit ausgewählt."));

        Map<String, Boolean> updates = new LinkedHashMap<>();
        for (AppModule module : AppModule.values()) {
            if (!module.implemented()) {
                continue;
            }
            updates.put(module.key(), params.containsKey("module_" + module.key()));
        }
        moduleSettingsService.saveModules(resolvedUnitId, updates);
        redirectAttributes.addFlashAttribute("saved", true);
        redirectAttributes.addFlashAttribute("message", "Module gespeichert.");
        return "redirect:/admin?scope=einheit&tab=module&unit=" + resolvedUnitId;
    }

    @PostMapping("/users")
    public String createUser(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "scope") String scope,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam String username,
            @RequestParam String displayName,
            @RequestParam(required = false) String loginEmail,
            @RequestParam String password,
            @RequestParam(defaultValue = "USER") UserRole role,
            @RequestParam(required = false) Long unitIdForm,
            @RequestParam(required = false) String cardUid,
            @RequestParam(required = false) String cardLabel,
            @RequestParam(required = false) Long organizationalRoleId,
            @RequestParam(name = "sendPasswordEmail", defaultValue = "false") boolean sendPasswordEmail,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            if ("global".equals(scope) && role == UserRole.USER) {
                throw new IllegalArgumentException(
                        "Im globalen Adminpanel können nur Superadmin- und Einheitsadmin-Konten angelegt werden.");
            }
            if ("einheit".equals(scope) && role == UserRole.SUPER_ADMIN) {
                throw new IllegalArgumentException(
                        "Superadmin kann nur im globalen Adminpanel vergeben werden.");
            }
            Long effectiveUnitId = resolveEffectiveUnitId(scope, unitId, unitIdForm, role, actor);
            User created = userManagementService.createUser(
                    username,
                    displayName,
                    loginEmail,
                    password,
                    role,
                    effectiveUnitId,
                    cardUid,
                    cardLabel,
                    organizationalRoleId,
                    actor,
                    request);
            String message = "Benutzer „" + created.getUsername() + "“ wurde angelegt.";
            message = appendMailNotice(message, created, password, sendPasswordEmail, false);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", message);
            return redirectAfterUser(scope, unitId, actor);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectAfterUser(scope, unitId, actor);
        }
    }

    @PostMapping("/users/{id}")
    public String updateUser(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam(name = "scope") String scope,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam String username,
            @RequestParam String displayName,
            @RequestParam(required = false) String loginEmail,
            @RequestParam UserRole role,
            @RequestParam(required = false) Long unitIdForm,
            @RequestParam(required = false) String active,
            @RequestParam(required = false) Long organizationalRoleId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            boolean isActive = "true".equalsIgnoreCase(active);
            if ("einheit".equals(scope) && role == UserRole.SUPER_ADMIN) {
                throw new IllegalArgumentException(
                        "Superadmin kann nur im globalen Adminpanel vergeben werden.");
            }
            Long effectiveUnitId = resolveEffectiveUnitId(scope, unitId, unitIdForm, role, actor);
            userManagementService.updateUser(
                    id,
                    username,
                    displayName,
                    loginEmail,
                    role,
                    effectiveUnitId,
                    isActive,
                    organizationalRoleId,
                    actor,
                    request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Benutzer wurde gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectAfterUser(scope, unitId, actor);
    }

    @PostMapping("/users/{id}/dienstgrad")
    public String assignDienstgrad(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam(name = "scope") String scope,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "dienstgradRoleId", required = false) Long dienstgradRoleId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            userManagementService.assignDienstgrad(id, dienstgradRoleId, actor, request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Dienstgrad gespeichert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectAfterUser(scope, unitId, actor);
    }

    @PostMapping("/users/{id}/functions/assign")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<java.util.Map<String, String>> assignFunction(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam long roleId,
            HttpServletRequest request) {
        try {
            userManagementService.assignFunction(id, roleId, actor, request);
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of("message", "Funktion zugewiesen"));
        } catch (IllegalArgumentException e) {
            return org.springframework.http.ResponseEntity.badRequest()
                    .body(java.util.Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/users/{id}/functions/remove")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<java.util.Map<String, String>> removeFunction(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam long roleId,
            HttpServletRequest request) {
        try {
            userManagementService.removeFunction(id, roleId, actor, request);
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of("message", "Funktion entfernt"));
        } catch (IllegalArgumentException e) {
            return org.springframework.http.ResponseEntity.badRequest()
                    .body(java.util.Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/users/{id}/password")
    public String resetUserPassword(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam String newPassword,
            @RequestParam(name = "scope") String scope,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "sendPasswordEmail", defaultValue = "false") boolean sendPasswordEmail,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            userManagementService.setPasswordByAdmin(id, newPassword, actor, request);
            User user = userService.findByIdWithUnit(id).orElseThrow();
            String message = "Passwort wurde zurückgesetzt.";
            message = appendMailNotice(message, user, newPassword, sendPasswordEmail, true);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", message);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectAfterUser(scope, unitId, actor);
    }

    @PostMapping("/users/{id}/rfid")
    public String addRfid(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam(name = "scope") String scope,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam String cardUid,
            @RequestParam(required = false) String cardLabel,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            userManagementService.registerRfidCard(id, cardUid, cardLabel, actor, request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "RFID-Chip wurde registriert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectAfterUser(scope, unitId, actor);
    }

    @PostMapping("/users/{id}/rfid/{cardId}/revoke")
    public String revokeRfid(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @PathVariable long cardId,
            @RequestParam(name = "scope") String scope,
            @RequestParam(name = "unit", required = false) Long unitId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            userManagementService.revokeRfidCard(cardId, actor, request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "RFID-Chip wurde deaktiviert.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectAfterUser(scope, unitId, actor);
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(
            @AuthenticationPrincipal AppUserDetails actor,
            @PathVariable long id,
            @RequestParam(name = "scope") String scope,
            @RequestParam(name = "unit", required = false) Long unitId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            userManagementService.deleteUserByAdmin(id, actor, request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Benutzerkonto wurde gelöscht.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectAfterUser(scope, unitId, actor);
    }

    @GetMapping("/users/{id}/export")
    public ResponseEntity<byte[]> exportUser(
            @AuthenticationPrincipal AppUserDetails actor, @PathVariable long id) throws Exception {
        UserDataExport data = userManagementService.buildUserExport(id, actor);
        byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data).getBytes(StandardCharsets.UTF_8);
        String filename = "daten-export-" + data.username() + "-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @PostMapping("/test-mode")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String setTestMode(
            @RequestParam(name = "enabled", defaultValue = "false") String enabled,
            @RequestParam(name = "scope", defaultValue = "einheit") String scope,
            @RequestParam(name = "tab", defaultValue = "schnittstellen") String tab,
            @RequestParam(name = "unit", required = false) Long unitId,
            RedirectAttributes redirectAttributes) {
        boolean turnOn = "true".equalsIgnoreCase(enabled) || "on".equalsIgnoreCase(enabled) || "1".equals(enabled);
        try {
            if (turnOn) {
                testModeService.enable();
                redirectAttributes.addFlashAttribute("saved", true);
                redirectAttributes.addFlashAttribute(
                        "message",
                        "Testmodus ist aktiv. Neue und geänderte Fachdaten gelten nur als Testdaten.");
            } else {
                testModeService.disable();
                redirectAttributes.addFlashAttribute("saved", true);
                redirectAttributes.addFlashAttribute(
                        "message", "Testmodus beendet. Alle Teständerungen wurden verworfen.");
            }
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", "Testmodus konnte nicht umgeschaltet werden: " + e.getMessage());
        }
        return buildAdminRedirect(scope, tab, unitId);
    }


    private void populateUnitUsersTab(Model model, AppUserDetails actor, long unitId) {
        unitRoleService.ensureSystemRoles(unitId);
        List<User> users = userManagementService.listAccounts(actor, unitId);
        populateAdminUsersTab(model, users, actor);
        model.addAttribute("unitDienstgrade", unitRoleService.listDienstgrade(unitId));
        model.addAttribute("unitFunktionen", unitRoleService.listFunktionen(unitId));
        model.addAttribute("showUnitUserRoles", true);
        Map<Long, List<de.feuerwehr.manager.unit.UnitRole>> functionsByUserId = new LinkedHashMap<>();
        for (User user : users) {
            if (user.getRole() == UserRole.USER) {
                functionsByUserId.put(user.getId(), userManagementService.listUserFunctions(user.getId()));
            }
        }
        model.addAttribute("functionsByUserId", functionsByUserId);
        Map<Long, String> functionPermissionLabels = new LinkedHashMap<>();
        for (de.feuerwehr.manager.unit.UnitRole fn : unitRoleService.listFunktionen(unitId)) {
            functionPermissionLabels.put(fn.getId(), unitRoleService.formatPermissionsLabel(fn));
        }
        model.addAttribute("functionPermissionLabels", functionPermissionLabels);
    }

    private void populateAdminUsersTab(Model model, List<User> users, AppUserDetails actor) {
        model.addAttribute("adminUsers", users);
        Map<Long, List<UserRfidCard>> rfidMap = new LinkedHashMap<>();
        Map<Long, Boolean> canManage = new LinkedHashMap<>();
        for (User user : users) {
            rfidMap.put(user.getId(), userManagementService.listRfidCards(user.getId()));
            canManage.put(user.getId(), accessControlService.canManageUser(actor, user));
        }
        model.addAttribute("rfidCardsByUserId", rfidMap);
        model.addAttribute("canManageUserById", canManage);
        model.addAttribute("smtpConfigured", accountMailService.canSendMail());
    }

    private String appendMailNotice(
            String baseMessage, User user, String plainPassword, boolean sendMail, boolean passwordReset) {
        if (!sendMail) {
            return baseMessage;
        }
        Optional<String> mailError = accountMailService.sendPasswordNotification(user, plainPassword, passwordReset);
        if (mailError.isEmpty()) {
            return baseMessage + " Passwort wurde per E-Mail versendet.";
        }
        return baseMessage + " " + mailError.get();
    }

    private static Long resolveEffectiveUnitId(
            String scope, Long scopeUnitId, Long unitIdForm, UserRole role, AppUserDetails actor) {
        if ("global".equals(scope)) {
            return emptyToNull(unitIdForm);
        }
        if (actor != null && actor.getRole().isSuperAdmin()) {
            Long fromForm = emptyToNull(unitIdForm);
            if (role == UserRole.SUPER_ADMIN) {
                return fromForm;
            }
            return fromForm != null ? fromForm : scopeUnitId;
        }
        return scopeUnitId;
    }

    private static Long emptyToNull(Long unitId) {
        return unitId == null || unitId <= 0 ? null : unitId;
    }

    private void populateGlobalUserFormModel(Model model) {
        model.addAttribute("assignableRoles", List.of(UserRole.SUPER_ADMIN, UserRole.UNIT_ADMIN));
        model.addAttribute("units", unitService.findActiveOrdered());
        model.addAttribute("userFormUnitId", null);
        model.addAttribute("roleLabels", UserRoleLabels.class);
        model.addAttribute("adminUsersGlobalOnly", true);
        model.addAttribute("showUnitPicker", true);
    }

    private void populateUserFormModel(AppUserDetails actor, Model model, Long scopeUnitId) {
        model.addAttribute("assignableRoles", resolveAssignableRoles(actor, scopeUnitId));
        model.addAttribute("units", unitService.findActiveOrdered(actor));
        model.addAttribute("userFormUnitId", scopeUnitId);
        model.addAttribute("roleLabels", UserRoleLabels.class);
        model.addAttribute("showUnitPicker", actor.getRole().isSuperAdmin() && scopeUnitId == null);
        model.addAttribute("showUnitPickerInUnit", actor.getRole().isSuperAdmin() && scopeUnitId != null);
        if (scopeUnitId != null) {
            unitRoleService.ensureSystemRoles(scopeUnitId);
            model.addAttribute("unitDienstgrade", unitRoleService.listDienstgrade(scopeUnitId));
            model.addAttribute("showUnitUserRoles", true);
        }
    }

    private static List<UserRole> resolveAssignableRoles(AppUserDetails actor, Long scopeUnitId) {
        if (scopeUnitId != null) {
            if (actor.getRole().isSuperAdmin()) {
                return List.of(UserRole.USER, UserRole.UNIT_ADMIN);
            }
            if (actor.getRole().isUnitAdmin()) {
                return List.of(UserRole.USER, UserRole.UNIT_ADMIN);
            }
            return List.of();
        }
        if (actor.getRole().isSuperAdmin()) {
            return List.of(UserRole.SUPER_ADMIN, UserRole.UNIT_ADMIN);
        }
        return UserRole.assignableBy(actor.getRole()).stream().sorted().toList();
    }

    private static String redirectAfterUser(String scope, Long unitId, AppUserDetails actor) {
        if ("global".equals(scope)) {
            return "redirect:/admin?scope=global&tab=benutzer";
        }
        if (unitId != null) {
            return "redirect:/admin?scope=einheit&tab=benutzer&unit=" + unitId;
        }
        return "redirect:/admin?scope=einheit&tab=benutzer";
    }

    private static String buildAdminRedirect(String scope, String tab, Long unitId) {
        StringBuilder url = new StringBuilder("redirect:/admin?scope=").append(scope).append("&tab=").append(tab);
        if (unitId != null && "einheit".equals(scope)) {
            url.append("&unit=").append(unitId);
        }
        return url.toString();
    }

    private static String normalizeGlobalTab(String tab) {
        return switch (tab) {
            case "konfiguration", "benutzer", "einheiten", "schnittstellen", "audit", "container-log" -> tab;
            default -> "konfiguration";
        };
    }

    private static String normalizeUnitTab(String tab) {
        return switch (tab) {
            case "konfiguration", "benutzer", "rollen", "schnittstellen", "module", "technik", "ausbildung" -> tab;
            case "personal" -> "ausbildung";
            default -> "konfiguration";
        };
    }

    static String normalizeVehicleSubTab(String tab) {
        if (tab == null || tab.isBlank()) {
            return "uebersicht";
        }
        return switch (tab.toLowerCase()) {
            case "geraete" -> "geraete";
            case "checklisten" -> "checklisten";
            default -> "uebersicht";
        };
    }
}
