package de.feuerwehr.manager.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.util.ArrayList;
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
                payload.add(Map.of(
                        "type", p.type().name(),
                        "x", p.x(),
                        "y", p.y(),
                        "w", p.w(),
                        "h", p.h()));
            }
            user.setDashboardLayoutJson(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException("Dashboard-Layout konnte nicht gespeichert werden", e);
        }
        userRepository.save(user);
        return layout;
    }

    public boolean isAllowed(AppUserDetails actor, long unitId, DashboardWidgetType type) {
        if (type == null || actor == null) {
            return false;
        }
        if (type.adminOnly() && !actor.getRole().isAdminLevel()) {
            return false;
        }
        if (type.requiredModule() != null
                && !moduleSettingsService.isEnabled(type.requiredModule(), unitId)) {
            return false;
        }
        if (type.requiredPermission() != null
                && !userPermissionService.hasPermission(actor, unitId, type.requiredPermission())) {
            return false;
        }
        return true;
    }

    /** Nächste freie Zeile unterhalb aller vorhandenen Widgets. */
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

    private static DashboardWidgetPlacement fromJsonNode(JsonNode node, int cascadeY) {
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
        if (node.has("x") || node.has("y") || node.has("w") || node.has("h")) {
            return new DashboardWidgetPlacement(
                    type,
                    intOr(node.get("x"), 0),
                    intOr(node.get("y"), cascadeY),
                    intOr(node.get("w"), DashboardWidgetPlacement.defaultFor(type, 0).w()),
                    intOr(node.get("h"), DashboardWidgetPlacement.defaultFor(type, 0).h()));
        }
        if (node.has("size")) {
            int w = DashboardWidgetPlacement.widthFromLegacySize(textOrNull(node.get("size")));
            int h = DashboardWidgetPlacement.defaultFor(type, 0).h();
            int x = Math.max(0, DashboardWidgetPlacement.COLS - w);
            if (w >= 8) {
                x = 0;
            } else if (w == 6) {
                x = 0;
            }
            // Termine legacy "NARROW" → rechts
            if (type == DashboardWidgetType.TERMINE && w <= 4) {
                x = 8;
            }
            return new DashboardWidgetPlacement(type, x, cascadeY, w, h);
        }
        return DashboardWidgetPlacement.defaultFor(type, cascadeY);
    }

    private static DashboardWidgetPlacement fromRawMap(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        Object typeRaw = raw.get("type");
        DashboardWidgetType type = DashboardWidgetType.fromId(typeRaw == null ? null : String.valueOf(typeRaw));
        if (type == null) {
            return null;
        }
        DashboardWidgetPlacement defaults = DashboardWidgetPlacement.defaultFor(type, 0);
        return new DashboardWidgetPlacement(
                type,
                toInt(raw.get("x"), defaults.x()),
                toInt(raw.get("y"), defaults.y()),
                toInt(raw.get("w"), defaults.w()),
                toInt(raw.get("h"), defaults.h()));
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
