package de.feuerwehr.manager.web;

import de.feuerwehr.manager.mail.AccountMailService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.SecurityProperties;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

@ControllerAdvice
@RequiredArgsConstructor
public class WebUiAdvice {

    private final SecurityProperties securityProperties;
    private final AccountMailService accountMailService;
    private final TestModeService testModeService;
    private final UnitService unitService;
    private final ModuleSettingsService moduleSettingsService;
    private final GlobalSettingsService globalSettingsService;
    private final UserRepository userRepository;

    @ModelAttribute("brandName")
    public String brandName() {
        String name = globalSettingsService.get().getFfName();
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return "Feuerwehr-Manager";
    }

    @ModelAttribute("customLogoDataUri")
    public String customLogoDataUri() {
        if (!globalSettingsService.hasCustomLogo()) {
            return null;
        }
        return globalSettingsService.get().getLogoBase64();
    }

    @ModelAttribute("currentUserId")
    public Long currentUserId(@AuthenticationPrincipal AppUserDetails user) {
        return user != null ? user.getUserId() : null;
    }

    @ModelAttribute("isSuperAdmin")
    public boolean isSuperAdmin(@AuthenticationPrincipal AppUserDetails user) {
        return user != null && user.getRole().isSuperAdmin();
    }

    @ModelAttribute("isUnitAdmin")
    public boolean isUnitAdmin(@AuthenticationPrincipal AppUserDetails user) {
        return user != null && user.getRole().isUnitAdmin();
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin(@AuthenticationPrincipal AppUserDetails user) {
        return user != null && user.getRole().isAdminLevel();
    }

    @ModelAttribute("canManageUnits")
    public boolean canManageUnits(@AuthenticationPrincipal AppUserDetails user) {
        return user != null && user.getRole().isSuperAdmin();
    }

    @ModelAttribute("testModeEnabled")
    public boolean testModeEnabled() {
        return testModeService.isEnabled();
    }

    @ModelAttribute("minPasswordLength")
    public int minPasswordLength() {
        return securityProperties.minPasswordLength();
    }

    @ModelAttribute("userTheme")
    public String userTheme(@AuthenticationPrincipal AppUserDetails user) {
        if (user == null) {
            return null;
        }
        return userRepository.findById(user.getUserId())
                .map(User::getTheme)
                .filter(t -> "light".equals(t) || "dark".equals(t))
                .orElse("light");
    }

    @ModelAttribute
    public void addActiveUnitContext(
            @AuthenticationPrincipal AppUserDetails user,
            @RequestParam(name = "unit", required = false) Long unitParam,
            HttpServletRequest request,
            Model model) {
        if (user == null) {
            return;
        }
        model.addAttribute("currentRequestPath", buildRequestPath(request));
        model.addAttribute("unitSwitchDisabled", !user.getRole().isSuperAdmin());
        List<Unit> units = unitService.findActiveOrdered(user);
        model.addAttribute("units", units);
        unitService.resolveActiveUnit(unitParam, user).ifPresent(u -> {
            model.addAttribute("unitId", u.getId());
            model.addAttribute("currentUnitName", u.getName());
            model.addAttribute("smtpConfigured", accountMailService.canSendMailForUnit(u.getId()));
        });
    }

    @ModelAttribute
    public void addModuleNavigation(
            @AuthenticationPrincipal AppUserDetails user,
            @RequestParam(name = "unit", required = false) Long unitParam,
            Model model) {
        Long activeUnitId = null;
        if (user != null) {
            activeUnitId = unitService.resolveActiveUnit(unitParam, user).map(Unit::getId).orElse(null);
        }
        if (activeUnitId == null) {
            activeUnitId = (Long) model.getAttribute("unitId");
        }
        for (AppModule module : AppModule.values()) {
            boolean enabled = activeUnitId != null && moduleSettingsService.isEnabled(module, activeUnitId);
            String state;
            if (activeUnitId == null) {
                state = module == AppModule.PERSONAL && module.implemented() ? "link" : "hidden";
            } else if (!enabled) {
                state = "hidden";
            } else if (module.implemented()) {
                state = "link";
            } else {
                state = "soon";
            }
            model.addAttribute("nav" + capitalize(module.key()), state);
        }
    }

    @ModelAttribute("activeNav")
    public String activeNav(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/personal")) {
            return "personal";
        }
        if (path.startsWith("/my-area") || path.startsWith("/profile")) {
            return "my-area";
        }
        if (path.startsWith("/test-alarm")) {
            return "test-alarm";
        }
        if (path.startsWith("/admin")) {
            return "admin";
        }
        if (path.startsWith("/settings")) {
            return "settings";
        }
        if ("/".equals(path)) {
            return "home";
        }
        return "";
    }

    private static String capitalize(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    private static String buildRequestPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return path;
        }
        return path + "?" + query;
    }
}
