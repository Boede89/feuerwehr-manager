package de.feuerwehr.manager.web;

import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.SecurityProperties;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
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
    private final TestModeService testModeService;
    private final UnitService unitService;

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
        });
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
        if (path.startsWith("/settings")) {
            return "settings";
        }
        if ("/".equals(path)) {
            return "home";
        }
        return "";
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
