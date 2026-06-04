package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitDiveraSettings;
import de.feuerwehr.manager.unit.UnitDiveraSettingsRepository;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserManagementService;
import de.feuerwehr.manager.user.UserRole;
import de.feuerwehr.manager.user.UserRoleLabels;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
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
    private final UnitDiveraSettingsRepository diveraSettingsRepository;
    private final ModuleSettingsService moduleSettingsService;
    private final TestModeService testModeService;
    private final UserManagementService userManagementService;
    private final AdminGlobalViewService adminGlobalViewService;

    @GetMapping
    public String index(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestParam(name = "scope", defaultValue = "einheit") String scope,
            @RequestParam(name = "tab", required = false) String tab,
            @RequestParam(name = "createUser", defaultValue = "false") boolean showUserCreate,
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
            tab = normalizeUnitTab(tab != null ? tab : "schnittstellen");
        }

        model.addAttribute("adminScope", scope);
        model.addAttribute("adminTab", tab);
        model.addAttribute("showGlobalScope", superAdmin);
        model.addAttribute("showUserCreate", showUserCreate);
        model.addAttribute("testModeEnabled", testModeService.isEnabled());

        if ("global".equals(scope)) {
            populateGlobalUserFormModel(model);
            switch (tab) {
                case "benutzer" -> model.addAttribute("adminUsers", userManagementService.listAdminLevelAccounts());
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
        populateUserFormModel(actor, model, resolvedId);

        if ("schnittstellen".equals(tab)) {
            populateDivera(model, resolvedId);
        }
        if ("module".equals(tab)) {
            model.addAttribute("modulesEnabled", moduleSettingsService.modulesEnabled(resolvedId));
            model.addAttribute("moduleDefs", Arrays.asList(AppModule.values()));
        }
        if ("benutzer".equals(tab)) {
            model.addAttribute("adminUsers", userManagementService.listAccounts(actor, resolvedId));
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
            @RequestParam String password,
            @RequestParam(defaultValue = "USER") UserRole role,
            @RequestParam(required = false) Long unitIdForm,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            if ("global".equals(scope) && role == UserRole.USER) {
                throw new IllegalArgumentException(
                        "Im globalen Adminpanel können nur Superadmin- und Einheitsadmin-Konten angelegt werden.");
            }
            Long effectiveUnitId = "global".equals(scope) ? unitIdForm : unitId;
            User created = userManagementService.createUser(
                    username, displayName, password, role, effectiveUnitId, actor, request);
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute(
                    "message", "Benutzer „" + created.getUsername() + "“ wurde angelegt.");
            return redirectAfterUser(scope, unitId, actor);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectAfterUser(scope, unitId, actor) + "&createUser=true";
        }
    }

    @PostMapping("/test-mode")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String setTestMode(
            @RequestParam(name = "enabled", defaultValue = "false") boolean enabled,
            @RequestParam(name = "scope", defaultValue = "einheit") String scope,
            @RequestParam(name = "tab", defaultValue = "benutzer") String tab,
            @RequestParam(name = "unit", required = false) Long unitId,
            RedirectAttributes redirectAttributes) {
        if (enabled) {
            testModeService.enable();
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute(
                    "message", "Testmodus ist aktiv. Neue und geänderte Fachdaten gelten nur als Testdaten.");
        } else {
            testModeService.disable();
            redirectAttributes.addFlashAttribute("saved", true);
            redirectAttributes.addFlashAttribute("message", "Testmodus beendet. Alle Teständerungen wurden verworfen.");
        }
        return buildAdminRedirect(scope, tab, unitId);
    }

    @PostMapping("/divera")
    public String saveDivera(
            @AuthenticationPrincipal AppUserDetails actor,
            @RequestParam(name = "unit") long unitId,
            @RequestParam String apiBaseUrl,
            @RequestParam(required = false) String accessKey,
            RedirectAttributes redirectAttributes) {

        long resolvedUnitId = unitService
                .resolveActiveUnit(unitId, actor)
                .map(Unit::getId)
                .orElseThrow(() -> new IllegalArgumentException("Keine gültige Einheit ausgewählt."));

        UnitDiveraSettings settings = diveraSettingsRepository
                .findByUnitId(resolvedUnitId)
                .orElseThrow(() -> new IllegalArgumentException("Keine Divera-Einstellungen für diese Einheit."));

        String base = apiBaseUrl == null ? "" : apiBaseUrl.trim();
        if (base.isEmpty()) {
            base = "https://app.divera247.com";
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        settings.setApiBaseUrl(base);

        if (accessKey != null && !accessKey.isBlank()) {
            settings.setAccessKey(accessKey.trim().replaceAll("[\\r\\n\\t\\v]+", ""));
        }

        diveraSettingsRepository.save(settings);
        redirectAttributes.addFlashAttribute("saved", true);
        redirectAttributes.addFlashAttribute("message", "Divera-Einstellungen gespeichert.");
        return "redirect:/admin?scope=einheit&tab=schnittstellen&unit=" + resolvedUnitId;
    }

    private void populateDivera(Model model, long unitId) {
        Optional<UnitDiveraSettings> opt = diveraSettingsRepository.findByUnitId(unitId);
        if (opt.isPresent()) {
            UnitDiveraSettings s = opt.get();
            model.addAttribute("apiBaseUrl", s.getApiBaseUrl());
            model.addAttribute("accessKeyConfigured", s.getAccessKey() != null && !s.getAccessKey().isBlank());
        } else {
            model.addAttribute("apiBaseUrl", "https://app.divera247.com");
            model.addAttribute("accessKeyConfigured", false);
        }
    }

    private void populateGlobalUserFormModel(Model model) {
        model.addAttribute("assignableRoles", List.of(UserRole.SUPER_ADMIN, UserRole.UNIT_ADMIN));
        model.addAttribute("units", unitService.findActiveOrdered());
        model.addAttribute("userFormUnitId", null);
        model.addAttribute("roleLabels", UserRoleLabels.class);
        model.addAttribute("adminUsersGlobalOnly", true);
    }

    private void populateUserFormModel(AppUserDetails actor, Model model, Long scopeUnitId) {
        List<UserRole> roles = UserRole.assignableBy(actor.getRole()).stream().sorted().toList();
        model.addAttribute("assignableRoles", roles);
        model.addAttribute("units", unitService.findActiveOrdered(actor));
        model.addAttribute("userFormUnitId", scopeUnitId);
        model.addAttribute("roleLabels", UserRoleLabels.class);
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
            case "module", "schnittstellen", "personal", "benutzer" -> tab;
            default -> "schnittstellen";
        };
    }
}
