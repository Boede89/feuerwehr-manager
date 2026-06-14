package de.feuerwehr.manager.web;

import de.feuerwehr.manager.divera.DiveraService;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.termine.DashboardTerminWidgetView;
import de.feuerwehr.manager.termine.TermineService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private static final int DASHBOARD_TERMINE_LIMIT = 5;

    private final UnitService unitService;
    private final DiveraService diveraService;
    private final ModuleSettingsService moduleSettingsService;
    private final UserPermissionService userPermissionService;
    private final TermineService termineService;
    private final PersonRepository personRepository;
    private final TestModeService testModeService;

    @GetMapping("/")
    public String dashboard(
            @AuthenticationPrincipal AppUserDetails currentUser,
            @RequestParam(name = "unit", required = false) Long unitId,
            Model model) {
        model.addAttribute("currentUser", currentUser);
        if (unitService.findActiveOrdered(currentUser).isEmpty()) {
            return "redirect:/settings/units?setup=1";
        }
        Optional<Unit> unit = unitService.resolveActiveUnit(unitId, currentUser);
        if (unit.isEmpty()) {
            return "redirect:/settings/units?setup=1";
        }
        Unit resolved = unit.get();
        model.addAttribute("unitId", resolved.getId());
        model.addAttribute("currentUnitName", resolved.getName());
        model.addAttribute("divera", diveraService.getAlarmsForUnit(resolved.getId()));
        addTermineWidgetModel(currentUser, resolved.getId(), model);
        return "dashboard";
    }

    private void addTermineWidgetModel(AppUserDetails currentUser, long unitId, Model model) {
        boolean showTermineWidget = moduleSettingsService.isEnabled(AppModule.TERMINE, unitId)
                && userPermissionService.hasPermission(currentUser, unitId, "termine.read");
        model.addAttribute("showTermineWidget", showTermineWidget);
        if (!showTermineWidget) {
            return;
        }

        var linkedPerson = personRepository.findActiveByUserIdAndUnitId(
                currentUser.getUserId(), unitId, testModeService.isEnabled());
        boolean hasLinkedPerson = linkedPerson.isPresent();
        model.addAttribute("hasLinkedPerson", hasLinkedPerson);
        List<DashboardTerminWidgetView> dashboardTermine = linkedPerson
                .map(person -> termineService.listUpcomingDashboardTermine(
                        unitId, person.getId(), DASHBOARD_TERMINE_LIMIT))
                .orElse(List.of());
        model.addAttribute("dashboardTermine", dashboardTermine);
    }
}
