package de.feuerwehr.manager.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.auswertung.AuswertungOverviewStats;
import de.feuerwehr.manager.auswertung.AuswertungService;
import de.feuerwehr.manager.auswertung.DashboardParticipationStats;
import de.feuerwehr.manager.berichte.AttendanceCheckInService;
import de.feuerwehr.manager.berichte.UnitAddressSupport;
import de.feuerwehr.manager.dashboard.DashboardLayoutService;
import de.feuerwehr.manager.dashboard.DashboardWidgetCatalogItem;
import de.feuerwehr.manager.dashboard.DashboardWidgetPlacement;
import de.feuerwehr.manager.dashboard.DashboardWidgetType;
import de.feuerwehr.manager.divera.DiveraAlarmsResponse;
import de.feuerwehr.manager.divera.DiveraService;
import de.feuerwehr.manager.divera.ManualAlarmService;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private static final int DASHBOARD_TERMINE_LIMIT = 5;

    private final UnitService unitService;
    private final DiveraService diveraService;
    private final ManualAlarmService manualAlarmService;
    private final ModuleSettingsService moduleSettingsService;
    private final UserPermissionService userPermissionService;
    private final TermineService termineService;
    private final PersonRepository personRepository;
    private final TestModeService testModeService;
    private final AttendanceCheckInService attendanceCheckInService;
    private final AuswertungService auswertungService;
    private final DashboardLayoutService dashboardLayoutService;
    private final ObjectMapper objectMapper;

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
        long resolvedUnitId = resolved.getId();
        model.addAttribute("unitId", resolvedUnitId);
        model.addAttribute("currentUnitName", resolved.getName());

        List<DashboardWidgetPlacement> placements =
                dashboardLayoutService.resolveActivePlacements(currentUser, resolvedUnitId);
        model.addAttribute("dashboardWidgets", placements);
        model.addAttribute("dashboardCatalog", dashboardLayoutService.catalog(currentUser, resolvedUnitId));
        try {
            model.addAttribute(
                    "dashboardCatalogJson",
                    objectMapper.writeValueAsString(
                            dashboardLayoutService.catalog(currentUser, resolvedUnitId)));
        } catch (Exception e) {
            model.addAttribute("dashboardCatalogJson", "[]");
        }

        Set<DashboardWidgetType> needed = new LinkedHashSet<>();
        for (DashboardWidgetPlacement p : placements) {
            needed.add(p.type());
        }
        model.addAttribute("canManageManualAlarms", currentUser.getRole().isAdminLevel());
        if (needed.contains(DashboardWidgetType.DIVERA)
                || needed.contains(DashboardWidgetType.PLANNED_ALARMS)) {
            loadEinsatzData(model, currentUser, resolved);
        } else {
            model.addAttribute("divera", DiveraAlarmsResponse.fail(""));
            model.addAttribute("manualDraftAlarms", List.of());
        }

        if (needed.contains(DashboardWidgetType.TERMINE)) {
            loadTermineData(currentUser, resolvedUnitId, model);
        } else {
            model.addAttribute("hasLinkedPerson", false);
            model.addAttribute("dashboardTermine", List.of());
        }

        if (needed.contains(DashboardWidgetType.MY_STATS)) {
            loadParticipationData(currentUser, resolvedUnitId, model);
        } else {
            model.addAttribute("participationHasPerson", false);
            model.addAttribute("myParticipation", null);
            model.addAttribute("participationYear", LocalDate.now().getYear());
        }

        if (needed.contains(DashboardWidgetType.UNIT_OVERVIEW)) {
            loadUnitOverview(resolvedUnitId, model);
        } else {
            model.addAttribute("unitOverviewStats", null);
            model.addAttribute("unitOverviewYear", LocalDate.now().getYear());
        }

        return "dashboard";
    }

    @PostMapping("/dashboard/layout")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveLayout(
            @AuthenticationPrincipal AppUserDetails currentUser,
            @RequestParam(name = "unit", required = false) Long unitId,
            @RequestBody Map<String, Object> body) {
        Optional<Unit> unit = unitService.resolveActiveUnit(unitId, currentUser);
        if (unit.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Keine Einheit ausgewählt"));
        }
        List<Map<String, String>> widgets = parseWidgetPayload(body.get("widgets"));
        List<DashboardWidgetPlacement> saved =
                dashboardLayoutService.saveLayout(currentUser, unit.get().getId(), widgets);
        List<DashboardWidgetCatalogItem> catalog =
                dashboardLayoutService.catalog(currentUser, unit.get().getId());
        List<Map<String, String>> responseWidgets = saved.stream()
                .map(p -> Map.of("type", p.type().name(), "size", p.size().name()))
                .toList();
        return ResponseEntity.ok(Map.of(
                "message", "Startseite gespeichert",
                "widgets", responseWidgets,
                "catalog", catalog));
    }

    private static List<Map<String, String>> parseWidgetPayload(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String s) {
                result.add(Map.of("type", s));
                continue;
            }
            if (item instanceof Map<?, ?> map) {
                Object type = map.get("type");
                Object size = map.get("size");
                if (type == null) {
                    continue;
                }
                if (size == null) {
                    result.add(Map.of("type", String.valueOf(type)));
                } else {
                    result.add(Map.of("type", String.valueOf(type), "size", String.valueOf(size)));
                }
            }
        }
        return result;
    }

    private void loadEinsatzData(Model model, AppUserDetails currentUser, Unit unit) {
        try {
            model.addAttribute("divera", diveraService.getAlarmsForUnit(unit.getId()));
        } catch (Exception e) {
            log.warn("DIVERA-Widget konnte nicht geladen werden: {}", e.getMessage(), e);
            model.addAttribute("divera", DiveraAlarmsResponse.fail("DIVERA-Abgleich fehlgeschlagen"));
        }
        model.addAttribute("canManageManualAlarms", currentUser.getRole().isAdminLevel());
        try {
            model.addAttribute("manualDraftAlarms", manualAlarmService.listDraftSummariesForUnit(unit.getId()));
            model.addAttribute("geraetehausAddress", UnitAddressSupport.fullAddressLine(unit));
        } catch (Exception e) {
            log.warn("Geplante Einsätze konnten nicht geladen werden: {}", e.getMessage());
            model.addAttribute("manualDraftAlarms", List.of());
        }
    }

    private void loadParticipationData(AppUserDetails currentUser, long unitId, Model model) {
        int year = LocalDate.now().getYear();
        model.addAttribute("participationYear", year);
        try {
            var linkedPerson = personRepository.findActiveByUserIdAndUnitId(
                    currentUser.getUserId(), unitId, testModeService.isEnabled());
            if (linkedPerson.isEmpty()) {
                model.addAttribute("participationHasPerson", false);
                model.addAttribute("myParticipation", null);
                return;
            }
            Optional<DashboardParticipationStats> stats = auswertungService.participationStatsForPerson(
                    unitId, linkedPerson.get().getId(), year);
            model.addAttribute("participationHasPerson", true);
            model.addAttribute("myParticipation", stats.orElse(null));
        } catch (Exception e) {
            log.warn("Beteiligungsstatistik konnte nicht geladen werden: {}", e.getMessage(), e);
            model.addAttribute("participationHasPerson", false);
            model.addAttribute("myParticipation", null);
        }
    }

    private void loadTermineData(AppUserDetails currentUser, long unitId, Model model) {
        try {
            var linkedPerson = personRepository.findActiveByUserIdAndUnitId(
                    currentUser.getUserId(), unitId, testModeService.isEnabled());
            boolean hasLinkedPerson = linkedPerson.isPresent();
            model.addAttribute("hasLinkedPerson", hasLinkedPerson);
            List<DashboardTerminWidgetView> dashboardTermine = linkedPerson
                    .map(person -> termineService.listUpcomingDashboardTermine(
                            unitId, person.getId(), DASHBOARD_TERMINE_LIMIT))
                    .orElse(List.of());
            boolean canWriteBerichte = moduleSettingsService.isEnabled(AppModule.BERICHTE, unitId)
                    && userPermissionService.hasPermission(currentUser, unitId, "berichte.write");
            if (canWriteBerichte && !dashboardTermine.isEmpty()) {
                dashboardTermine = attendanceCheckInService.enrichDashboardTermineForCheckIn(
                        unitId, dashboardTermine, true);
            } else {
                dashboardTermine = dashboardTermine.stream()
                        .map(t -> t.withCheckIn(false, null))
                        .toList();
            }
            model.addAttribute("dashboardTermine", dashboardTermine);
        } catch (Exception e) {
            log.warn("Termine-Widget konnte nicht geladen werden: {}", e.getMessage(), e);
            model.addAttribute("hasLinkedPerson", false);
            model.addAttribute("dashboardTermine", List.of());
        }
    }

    private void loadUnitOverview(long unitId, Model model) {
        int year = LocalDate.now().getYear();
        model.addAttribute("unitOverviewYear", year);
        try {
            model.addAttribute("unitOverviewStats", auswertungService.overviewStats(unitId, year));
        } catch (Exception e) {
            log.warn("Einheiten-Kennzahlen konnten nicht geladen werden: {}", e.getMessage(), e);
            model.addAttribute("unitOverviewStats", AuswertungOverviewStats.empty());
        }
    }
}
