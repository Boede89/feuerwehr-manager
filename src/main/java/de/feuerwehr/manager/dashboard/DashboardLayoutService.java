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
            DashboardWidgetPlacement.of(DashboardWidgetType.MY_STATS),
            new DashboardWidgetPlacement(DashboardWidgetType.DIVERA, DashboardWidgetSize.WIDE),
            new DashboardWidgetPlacement(DashboardWidgetType.TERMINE, DashboardWidgetSize.NARROW));

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
            AppUserDetails actor, long unitId, List<Map<String, String>> widgets) {
        User user = userRepository.findById(actor.getUserId()).orElseThrow();
        LinkedHashMap<DashboardWidgetType, DashboardWidgetSize> cleaned = new LinkedHashMap<>();
        if (widgets != null) {
            for (Map<String, String> raw : widgets) {
                if (raw == null) {
                    continue;
                }
                DashboardWidgetType type = DashboardWidgetType.fromId(raw.get("type"));
                if (type == null || !isAllowed(actor, unitId, type) || cleaned.containsKey(type)) {
                    continue;
                }
                DashboardWidgetSize size = DashboardWidgetSize.fromId(raw.get("size"));
                if (size == null) {
                    size = DashboardWidgetSize.defaultFor(type);
                }
                cleaned.put(type, size);
            }
        }
        List<DashboardWidgetPlacement> layout = cleaned.entrySet().stream()
                .map(e -> new DashboardWidgetPlacement(e.getKey(), e.getValue()))
                .toList();
        try {
            List<Map<String, String>> payload = new ArrayList<>();
            for (DashboardWidgetPlacement p : layout) {
                payload.add(Map.of("type", p.type().name(), "size", p.size().name()));
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
            for (JsonNode node : root) {
                DashboardWidgetType type;
                DashboardWidgetSize size;
                if (node.isTextual()) {
                    type = DashboardWidgetType.fromId(node.asText());
                    size = DashboardWidgetSize.defaultFor(type);
                } else if (node.isObject()) {
                    type = DashboardWidgetType.fromId(textOrNull(node.get("type")));
                    size = DashboardWidgetSize.fromId(textOrNull(node.get("size")));
                    if (size == null) {
                        size = DashboardWidgetSize.defaultFor(type);
                    }
                } else {
                    continue;
                }
                if (type == null || !seen.add(type)) {
                    continue;
                }
                result.add(new DashboardWidgetPlacement(type, size));
            }
            return result;
        } catch (Exception e) {
            log.warn("Ungültiges dashboard_layout_json: {}", e.getMessage());
            return List.of();
        }
    }

    private static String textOrNull(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }
}
