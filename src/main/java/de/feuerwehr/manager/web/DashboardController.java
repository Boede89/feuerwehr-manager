package de.feuerwehr.manager.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.auswertung.AuswertungOverviewStats;
import de.feuerwehr.manager.auswertung.AuswertungService;
import de.feuerwehr.manager.auswertung.DashboardParticipationStats;
import de.feuerwehr.manager.berichte.AttendanceCheckInService;
import de.feuerwehr.manager.berichte.UnitAddressSupport;
import de.feuerwehr.manager.dashboard.AtemschutzWidgetConfig;
import de.feuerwehr.manager.dashboard.DashboardLayoutService;
import de.feuerwehr.manager.dashboard.DashboardWidgetCatalogItem;
import de.feuerwehr.manager.dashboard.DashboardWidgetPlacement;
import de.feuerwehr.manager.dashboard.DashboardWidgetType;
import de.feuerwehr.manager.dashboard.OpenReportsWidgetConfig;
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
        model.addAttribute("dashboardCols", DashboardWidgetPlacement.COLS);

        List<DashboardWidgetPlacement> placements;
        try {
            placements = dashboardLayoutService.resolveActivePlacements(currentUser, resolvedUnitId);
        } catch (Exception e) {
            log.warn("Dashboard-Layout konnte nicht geladen werden: {}", e.getMessage(), e);
            placements = List.of(
                    DashboardWidgetPlacement.defaultFor(DashboardWidgetType.MY_STATS, 0),
                    DashboardWidgetPlacement.defaultFor(DashboardWidgetType.DIVERA, 5),
                    DashboardWidgetPlacement.defaultFor(DashboardWidgetType.TERMINE, 5));
        }
        model.addAttribute("dashboardWidgets", placements);
        try {
            model.addAttribute("dashboardCatalog", dashboardLayoutService.catalog(currentUser, resolvedUnitId));
            model.addAttribute(
                    "dashboardCatalogJson",
                    objectMapper.writeValueAsString(
                            dashboardLayoutService.catalog(currentUser, resolvedUnitId)));
        } catch (Exception e) {
            log.warn("Dashboard-Katalog konnte nicht geladen werden: {}", e.getMessage());
            model.addAttribute("dashboardCatalog", List.of());
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

        if (needed.contains(DashboardWidgetType.ATEMSCHUTZ)) {
            loadAtemschutzWidget(resolvedUnitId, placements, model);
        } else {
            model.addAttribute("atemschutzMetrics", List.of());
            model.addAttribute("atemschutzIncludePaused", false);
            model.addAttribute("atemschutzConfigJson", "{}");
        }
        try {
            model.addAttribute(
                    "atemschutzWidgetDefaultsJson",
                    objectMapper.writeValueAsString(AtemschutzWidgetConfig.defaults()));
        } catch (Exception e) {
            model.addAttribute("atemschutzWidgetDefaultsJson", "{}");
        }

        if (needed.contains(DashboardWidgetType.OPEN_REPORTS)) {
            loadOpenReportsWidget(resolvedUnitId, placements, model);
        } else {
            model.addAttribute("openReportItems", List.of());
            model.addAttribute("openReportsConfigJson", "{}");
        }
        try {
            model.addAttribute(
                    "openReportsWidgetDefaultsJson",
                    objectMapper.writeValueAsString(OpenReportsWidgetConfig.defaults()));
        } catch (Exception e) {
            model.addAttribute("openReportsWidgetDefaultsJson", "{}");
        }

        return "dashboard";
    }

    private void loadAtemschutzWidget(
            long unitId, List<DashboardWidgetPlacement> placements, Model model) {
        try {
            DashboardWidgetPlacement atemschutz = placements.stream()
                    .filter(p -> p.type() == DashboardWidgetType.ATEMSCHUTZ)
                    .findFirst()
                    .orElse(null);
            Map<String, Object> config =
                    atemschutz != null ? atemschutz.config() : AtemschutzWidgetConfig.defaults();
            model.addAttribute(
                    "atemschutzMetrics",
                    dashboardLayoutService.buildAtemschutzMetrics(unitId, config));
            model.addAttribute("atemschutzIncludePaused", AtemschutzWidgetConfig.includePaused(config));
            model.addAttribute("atemschutzConfigJson", objectMapper.writeValueAsString(
                    AtemschutzWidgetConfig.normalize(config)));
        } catch (Exception e) {
            log.warn("Atemschutz-Widget konnte nicht geladen werden: {}", e.getMessage(), e);
            model.addAttribute("atemschutzMetrics", List.of());
            model.addAttribute("atemschutzIncludePaused", false);
            model.addAttribute("atemschutzConfigJson", "{}");
        }
    }

    private void loadOpenReportsWidget(
            long unitId, List<DashboardWidgetPlacement> placements, Model model) {
        try {
            DashboardWidgetPlacement openReports = placements.stream()
                    .filter(p -> p.type() == DashboardWidgetType.OPEN_REPORTS)
                    .findFirst()
                    .orElse(null);
            Map<String, Object> config =
                    openReports != null ? openReports.config() : OpenReportsWidgetConfig.defaults();
            model.addAttribute(
                    "openReportItems",
                    dashboardLayoutService.buildOpenReportItems(unitId, config));
            model.addAttribute(
                    "openReportsConfigJson",
                    objectMapper.writeValueAsString(OpenReportsWidgetConfig.normalize(config)));
        } catch (Exception e) {
            log.warn("Offene-Berichte-Widget konnte nicht geladen werden: {}", e.getMessage(), e);
            model.addAttribute("openReportItems", List.of());
            model.addAttribute("openReportsConfigJson", "{}");
        }
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
        List<Map<String, Object>> widgets = parseWidgetPayload(body.get("widgets"));
        List<DashboardWidgetPlacement> saved =
                dashboardLayoutService.saveLayout(currentUser, unit.get().getId(), widgets);
        List<DashboardWidgetCatalogItem> catalog =
                dashboardLayoutService.catalog(currentUser, unit.get().getId());
        List<Map<String, Object>> responseWidgets = new ArrayList<>();
        for (DashboardWidgetPlacement p : saved) {
            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("type", p.type().name());
            entry.put("x", p.x());
            entry.put("y", p.y());
            entry.put("w", p.w());
            entry.put("h", p.h());
            if (p.config() != null && !p.config().isEmpty()) {
                entry.put("config", p.config());
            }
            responseWidgets.add(entry);
        }
        return ResponseEntity.ok(Map.of(
                "message", "Startseite gespeichert",
                "widgets", responseWidgets,
                "catalog", catalog));
    }

    private static List<Map<String, Object>> parseWidgetPayload(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String s) {
                result.add(Map.of("type", s));
                continue;
            }
            if (item instanceof Map<?, ?> map) {
                Object type = map.get("type");
                if (type == null) {
                    continue;
                }
                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("type", String.valueOf(type));
                for (String key : List.of("x", "y", "w", "h", "size", "config")) {
                    if (map.get(key) != null) {
                        entry.put(key, map.get(key));
                    }
                }
                result.add(entry);
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
            boolean berichteEnabled = moduleSettingsService.isEnabled(AppModule.BERICHTE, unitId);
            if (berichteEnabled && !dashboardTermine.isEmpty()) {
                dashboardTermine = attendanceCheckInService.enrichDashboardTermineForCheckIn(
                        unitId, dashboardTermine);
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
