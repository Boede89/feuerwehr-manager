package de.feuerwehr.manager.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.atemschutz.AtemschutzService;
import de.feuerwehr.manager.atemschutz.CarrierTauglichkeitStatus;
import de.feuerwehr.manager.berichte.AttendanceReport;
import de.feuerwehr.manager.berichte.AttendanceReportRepository;
import de.feuerwehr.manager.berichte.IncidentReport;
import de.feuerwehr.manager.berichte.IncidentReportRepository;
import de.feuerwehr.manager.berichte.IncidentReportStatus;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardLayoutService {

    private static final List<DashboardWidgetPlacement> DEFAULT_LAYOUT = List.of(
            DashboardWidgetPlacement.defaultFor(DashboardWidgetType.MY_STATS, 0),
            DashboardWidgetPlacement.defaultFor(DashboardWidgetType.DIVERA, 5),
            DashboardWidgetPlacement.defaultFor(DashboardWidgetType.TERMINE, 5));

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final ModuleSettingsService moduleSettingsService;
    private final UserPermissionService userPermissionService;
    private final AtemschutzService atemschutzService;
    private final IncidentReportRepository incidentReportRepository;
    private final AttendanceReportRepository attendanceReportRepository;
    private final TestModeService testModeService;

    @Transactional(readOnly = true)
    public List<DashboardWidgetPlacement> resolveActivePlacements(AppUserDetails actor, long unitId) {
        User user = userRepository.findById(actor.getUserId()).orElseThrow();
        boolean hasStoredLayout =
                user.getDashboardLayoutJson() != null && !user.getDashboardLayoutJson().isBlank();
        List<DashboardWidgetPlacement> stored = parseLayout(user.getDashboardLayoutJson());
        List<DashboardWidgetPlacement> source = hasStoredLayout ? stored : DEFAULT_LAYOUT;
        return filterAllowedUnique(actor, unitId, source, !hasStoredLayout);
    }

    @Transactional(readOnly = true)
    public List<DashboardWidgetCatalogItem> catalog(AppUserDetails actor, long unitId) {
        Set<DashboardWidgetType> active = new LinkedHashSet<>();
        for (DashboardWidgetPlacement p : resolveActivePlacements(actor, unitId)) {
            active.add(p.type());
        }
        List<DashboardWidgetCatalogItem> items = new ArrayList<>();
        for (DashboardWidgetType type : DashboardWidgetType.values()) {
            if (!isAllowed(actor, unitId, type)) {
                continue;
            }
            items.add(new DashboardWidgetCatalogItem(
                    type.name(), type.label(), type.description(), active.contains(type)));
        }
        return List.copyOf(items);
    }

    @Transactional
    public List<DashboardWidgetPlacement> saveLayout(
            AppUserDetails actor, long unitId, List<Map<String, Object>> widgets) {
        User user = userRepository.findById(actor.getUserId()).orElseThrow();
        LinkedHashMap<DashboardWidgetType, DashboardWidgetPlacement> cleaned = new LinkedHashMap<>();
        if (widgets != null) {
            for (Map<String, Object> raw : widgets) {
                DashboardWidgetPlacement placement = fromRawMap(raw);
                if (placement == null
                        || !isAllowed(actor, unitId, placement.type())
                        || cleaned.containsKey(placement.type())) {
                    continue;
                }
                cleaned.put(placement.type(), placement);
            }
        }
        List<DashboardWidgetPlacement> layout = List.copyOf(cleaned.values());
        try {
            List<Map<String, Object>> payload = new ArrayList<>();
            for (DashboardWidgetPlacement p : layout) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("type", p.type().name());
                row.put("x", p.x());
                row.put("y", p.y());
                row.put("w", p.w());
                row.put("h", p.h());
                if (p.config() != null && !p.config().isEmpty()) {
                    row.put("config", p.config());
                }
                payload.add(row);
            }
            user.setDashboardLayoutJson(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException("Dashboard-Layout konnte nicht gespeichert werden", e);
        }
        userRepository.save(user);
        return layout;
    }

    @Transactional
    public List<DashboardAtemschutzMetricView> buildAtemschutzMetrics(long unitId, Map<String, Object> config) {
        Map<String, Object> cfg = AtemschutzWidgetConfig.normalize(config);
        boolean includePaused = AtemschutzWidgetConfig.includePaused(cfg);
        AtemschutzService.CarrierListResult result = atemschutzService.listCarrierOverviews(unitId, "all");
        AtemschutzService.CarrierListStats stats = includePaused ? result.statsAll() : result.stats();

        List<AtemschutzService.CarrierOverview> carriers = result.carriers().stream()
                .filter(row -> row != null && row.carrier() != null)
                .filter(row -> de.feuerwehr.manager.util.PersonMembership.isCurrentlyMember(
                        row.carrier().getPerson()))
                .filter(row -> includePaused
                        || row.carrier().getStatus()
                                == de.feuerwehr.manager.atemschutz.AtemschutzCarrierStatus.ACTIVE)
                .toList();

        List<DashboardAtemschutzMetricView> views = new ArrayList<>();
        for (Map<String, Object> metricCfg : AtemschutzWidgetConfig.metrics(cfg)) {
            if (!Boolean.TRUE.equals(metricCfg.get("show"))) {
                continue;
            }
            AtemschutzWidgetConfig.Metric metric =
                    AtemschutzWidgetConfig.Metric.fromKey(String.valueOf(metricCfg.get("key")));
            if (metric == null) {
                continue;
            }
            boolean showNames = Boolean.TRUE.equals(metricCfg.get("showNames"));
            int count = switch (metric) {
                case TOTAL -> stats.total();
                case TAUGLICH -> stats.tauglich();
                case WARNUNG -> stats.warnung();
                case UEBUNG_ABGELAUFEN -> stats.uebungAbgelaufen();
                case NICHT_TAUGLICH -> stats.nichtTauglich();
                case CSA -> stats.csaTauglich();
            };
            List<String> names = List.of();
            if (showNames) {
                names = carriers.stream()
                        .filter(row -> matchesMetric(row, metric))
                        .map(row -> row.carrier().getPerson().anwesenheitDisplayName())
                        .filter(n -> n != null && !n.isBlank())
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
            }
            views.add(new DashboardAtemschutzMetricView(
                    metric.key(),
                    metric.label(),
                    metric.cssModifier(),
                    metric.filter(),
                    count,
                    showNames,
                    names));
        }
        return List.copyOf(views);
    }

    @Transactional(readOnly = true)
    public List<DashboardOpenReportItem> buildOpenReportItems(long unitId, Map<String, Object> config) {
        Map<String, Object> cfg = OpenReportsWidgetConfig.normalize(config);
        boolean includeTest = testModeService.isEnabled();
        boolean openInEdit = OpenReportsWidgetConfig.openInEdit(cfg);
        int limit = OpenReportsWidgetConfig.limit(cfg);
        List<DashboardOpenReportItem> items = new ArrayList<>();

        if (OpenReportsWidgetConfig.showEinsatzberichte(cfg)) {
            for (IncidentReport report :
                    incidentReportRepository.findByUnitIdAndStatus(
                            unitId, IncidentReportStatus.ENTWURF, includeTest)) {
                if (report == null || report.getId() == null) {
                    continue;
                }
                String title = openReportTitle(report);
                String href = openInEdit
                        ? "/berichte/einsatzberichte/" + report.getId() + "/bearbeiten?unit=" + unitId
                        : "/berichte/einsatzberichte/" + report.getId() + "?unit=" + unitId;
                items.add(new DashboardOpenReportItem(
                        "einsatz",
                        "Einsatzbericht",
                        title,
                        report.getIncidentDate(),
                        blankToNull(report.getIncidentNumber()),
                        href));
            }
        }

        if (OpenReportsWidgetConfig.showAnwesenheitslisten(cfg)) {
            LocalDate toDate =
                    OpenReportsWidgetConfig.anwesenheitOnlyUntilToday(cfg) ? LocalDate.now() : null;
            for (AttendanceReport report :
                    attendanceReportRepository.findByUnitIdAndStatusOptionalToDate(
                            unitId, IncidentReportStatus.ENTWURF, toDate, includeTest)) {
                if (report == null || report.getId() == null) {
                    continue;
                }
                String title = blankToNull(report.getTitle());
                if (title == null) {
                    title = "Anwesenheitsliste";
                }
                String href = openInEdit
                        ? "/berichte/anwesenheitslisten/" + report.getId() + "/bearbeiten?unit=" + unitId
                        : "/berichte/anwesenheitslisten/" + report.getId() + "?unit=" + unitId;
                items.add(new DashboardOpenReportItem(
                        "anwesenheit",
                        "Anwesenheitsliste",
                        title,
                        report.getEventDate(),
                        blankToNull(report.getReportNumber()),
                        href));
            }
        }

        items.sort(Comparator
                .comparing(DashboardOpenReportItem::date, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(DashboardOpenReportItem::title, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        if (items.size() > limit) {
            return List.copyOf(items.subList(0, limit));
        }
        return List.copyOf(items);
    }

    private static String openReportTitle(IncidentReport report) {
        String stichwort = blankToNull(report.getStichwort());
        if (stichwort != null) {
            return stichwort;
        }
        String type = blankToNull(report.getIncidentTypeLabel());
        String location = blankToNull(report.getLocation());
        if (type != null && location != null) {
            return type + " · " + location;
        }
        if (type != null) {
            return type;
        }
        if (location != null) {
            return location;
        }
        return "Einsatzbericht";
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean matchesMetric(
            AtemschutzService.CarrierOverview row, AtemschutzWidgetConfig.Metric metric) {
        CarrierTauglichkeitStatus status = row.tauglichkeit();
        return switch (metric) {
            case TOTAL -> true;
            case TAUGLICH -> status == CarrierTauglichkeitStatus.TAUGLICH;
            case WARNUNG -> status == CarrierTauglichkeitStatus.WARNUNG;
            case UEBUNG_ABGELAUFEN -> status == CarrierTauglichkeitStatus.UEBUNG_ABGELAUFEN;
            case NICHT_TAUGLICH -> status == CarrierTauglichkeitStatus.NICHT_TAUGLICH;
            case CSA -> row.csaTauglich();
        };
    }

    public boolean isAllowed(AppUserDetails actor, long unitId, DashboardWidgetType type) {
        if (type == null || actor == null) {
            return false;
        }
        if (type.adminOnly() && !actor.getRole().isAdminLevel()) {
            return false;
        }
        if (type.requiredModule() != null) {
            if (!moduleSettingsService.isEnabled(type.requiredModule(), unitId)) {
                return false;
            }
            // Wie Top-Navigation: Modulzugriff (read/write/approve), nicht nur *.read
            if (!userPermissionService.hasModuleAccess(actor, unitId, type.requiredModule().key())) {
                return false;
            }
            return true;
        }
        if (type.requiredPermission() != null
                && !userPermissionService.hasPermission(actor, unitId, type.requiredPermission())) {
            return false;
        }
        return true;
    }

    public static int nextFreeRow(List<DashboardWidgetPlacement> existing) {
        int max = 0;
        for (DashboardWidgetPlacement p : existing) {
            max = Math.max(max, p.y() + p.h());
        }
        return max;
    }

    private List<DashboardWidgetPlacement> filterAllowedUnique(
            AppUserDetails actor,
            long unitId,
            List<DashboardWidgetPlacement> source,
            boolean fallbackToDefaultIfEmpty) {
        List<DashboardWidgetPlacement> visible = new ArrayList<>();
        Set<DashboardWidgetType> seen = new LinkedHashSet<>();
        for (DashboardWidgetPlacement placement : source) {
            if (placement == null || placement.type() == null) {
                continue;
            }
            if (!isAllowed(actor, unitId, placement.type()) || !seen.add(placement.type())) {
                continue;
            }
            visible.add(placement);
        }
        if (visible.isEmpty() && fallbackToDefaultIfEmpty) {
            for (DashboardWidgetPlacement placement : DEFAULT_LAYOUT) {
                if (isAllowed(actor, unitId, placement.type()) && seen.add(placement.type())) {
                    visible.add(placement);
                }
            }
        }
        return List.copyOf(visible);
    }

    private List<DashboardWidgetPlacement> parseLayout(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return List.of();
            }
            List<DashboardWidgetPlacement> result = new ArrayList<>();
            Set<DashboardWidgetType> seen = new LinkedHashSet<>();
            int cascadeY = 0;
            for (JsonNode node : root) {
                DashboardWidgetPlacement placement = fromJsonNode(node, cascadeY);
                if (placement == null || !seen.add(placement.type())) {
                    continue;
                }
                result.add(placement);
                cascadeY = Math.max(cascadeY, placement.y() + placement.h());
            }
            return result;
        } catch (Exception e) {
            log.warn("Ungültiges dashboard_layout_json: {}", e.getMessage());
            return List.of();
        }
    }

    private DashboardWidgetPlacement fromJsonNode(JsonNode node, int cascadeY) {
        if (node == null) {
            return null;
        }
        if (node.isTextual()) {
            DashboardWidgetType type = DashboardWidgetType.fromId(node.asText());
            return type == null ? null : DashboardWidgetPlacement.defaultFor(type, cascadeY);
        }
        if (!node.isObject()) {
            return null;
        }
        DashboardWidgetType type = DashboardWidgetType.fromId(textOrNull(node.get("type")));
        if (type == null) {
            return null;
        }
        Map<String, Object> config = readConfig(node.get("config"), type);
        if (node.has("x") || node.has("y") || node.has("w") || node.has("h")) {
            return new DashboardWidgetPlacement(
                    type,
                    intOr(node.get("x"), 0),
                    intOr(node.get("y"), cascadeY),
                    intOr(node.get("w"), DashboardWidgetPlacement.defaultFor(type, 0).w()),
                    intOr(node.get("h"), DashboardWidgetPlacement.defaultFor(type, 0).h()),
                    config);
        }
        if (node.has("size")) {
            int w = DashboardWidgetPlacement.widthFromLegacySize(textOrNull(node.get("size")));
            int h = DashboardWidgetPlacement.defaultFor(type, 0).h();
            int x = 0;
            if (type == DashboardWidgetType.TERMINE && w <= 4) {
                x = 8;
            }
            return new DashboardWidgetPlacement(type, x, cascadeY, w, h, config);
        }
        return DashboardWidgetPlacement.defaultFor(type, cascadeY);
    }

    private DashboardWidgetPlacement fromRawMap(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        Object typeRaw = raw.get("type");
        DashboardWidgetType type = DashboardWidgetType.fromId(typeRaw == null ? null : String.valueOf(typeRaw));
        if (type == null) {
            return null;
        }
        DashboardWidgetPlacement defaults = DashboardWidgetPlacement.defaultFor(type, 0);
        Map<String, Object> config = Map.of();
        Object configRaw = raw.get("config");
        if (configRaw instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            config = normalizeConfig(type, copy);
        } else {
            config = defaultConfig(type);
        }
        return new DashboardWidgetPlacement(
                type,
                toInt(raw.get("x"), defaults.x()),
                toInt(raw.get("y"), defaults.y()),
                toInt(raw.get("w"), defaults.w()),
                toInt(raw.get("h"), defaults.h()),
                config);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readConfig(JsonNode node, DashboardWidgetType type) {
        if (node == null || node.isNull()) {
            return defaultConfig(type);
        }
        try {
            Map<String, Object> map = objectMapper.convertValue(node, Map.class);
            return normalizeConfig(type, map != null ? map : Map.of());
        } catch (Exception e) {
            return defaultConfig(type);
        }
    }

    private static Map<String, Object> defaultConfig(DashboardWidgetType type) {
        if (type == DashboardWidgetType.ATEMSCHUTZ) {
            return AtemschutzWidgetConfig.defaults();
        }
        if (type == DashboardWidgetType.OPEN_REPORTS) {
            return OpenReportsWidgetConfig.defaults();
        }
        return Map.of();
    }

    private static Map<String, Object> normalizeConfig(DashboardWidgetType type, Map<String, Object> raw) {
        if (type == DashboardWidgetType.ATEMSCHUTZ) {
            return AtemschutzWidgetConfig.normalize(raw);
        }
        if (type == DashboardWidgetType.OPEN_REPORTS) {
            return OpenReportsWidgetConfig.normalize(raw);
        }
        return raw == null || raw.isEmpty() ? Map.of() : Map.copyOf(raw);
    }

    private static String textOrNull(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private static int intOr(JsonNode node, int fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText().trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int toInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
