package de.feuerwehr.manager.dashboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardLayoutService {

    private static final List<DashboardWidgetType> DEFAULT_LAYOUT = List.of(
            DashboardWidgetType.MY_STATS,
            DashboardWidgetType.DIVERA,
            DashboardWidgetType.TERMINE,
            DashboardWidgetType.QUICK_LINKS);

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final ModuleSettingsService moduleSettingsService;
    private final UserPermissionService userPermissionService;

    @Transactional(readOnly = true)
    public List<DashboardWidgetType> resolveActiveWidgets(AppUserDetails actor, long unitId) {
        User user = userRepository.findById(actor.getUserId()).orElseThrow();
        boolean hasStoredLayout =
                user.getDashboardLayoutJson() != null && !user.getDashboardLayoutJson().isBlank();
        List<DashboardWidgetType> stored = parseLayout(user.getDashboardLayoutJson());
        List<DashboardWidgetType> source = hasStoredLayout ? stored : DEFAULT_LAYOUT;
        List<DashboardWidgetType> visible = new ArrayList<>();
        for (DashboardWidgetType type : source) {
            if (isAllowed(actor, unitId, type) && !visible.contains(type)) {
                visible.add(type);
            }
        }
        if (visible.isEmpty() && !hasStoredLayout) {
            for (DashboardWidgetType type : DEFAULT_LAYOUT) {
                if (isAllowed(actor, unitId, type) && !visible.contains(type)) {
                    visible.add(type);
                }
            }
        }
        return List.copyOf(visible);
    }

    @Transactional(readOnly = true)
    public List<DashboardWidgetCatalogItem> catalog(AppUserDetails actor, long unitId) {
        Set<DashboardWidgetType> active = new LinkedHashSet<>(resolveActiveWidgets(actor, unitId));
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
    public List<DashboardWidgetType> saveLayout(
            AppUserDetails actor, long unitId, List<String> widgetIds) {
        User user = userRepository.findById(actor.getUserId()).orElseThrow();
        LinkedHashSet<DashboardWidgetType> cleaned = new LinkedHashSet<>();
        if (widgetIds != null) {
            for (String raw : widgetIds) {
                DashboardWidgetType type = DashboardWidgetType.fromId(raw);
                if (type != null && isAllowed(actor, unitId, type)) {
                    cleaned.add(type);
                }
            }
        }
        List<DashboardWidgetType> layout = List.copyOf(cleaned);
        try {
            user.setDashboardLayoutJson(objectMapper.writeValueAsString(
                    layout.stream().map(Enum::name).toList()));
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

    private List<DashboardWidgetType> parseLayout(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> raw = objectMapper.readValue(json, new TypeReference<>() {});
            List<DashboardWidgetType> result = new ArrayList<>();
            for (String id : raw) {
                DashboardWidgetType type = DashboardWidgetType.fromId(id);
                if (type != null && !result.contains(type)) {
                    result.add(type);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Ungültiges dashboard_layout_json: {}", e.getMessage());
            return List.of();
        }
    }

    public List<DashboardQuickLink> buildQuickLinks(AppUserDetails actor, long unitId) {
        List<DashboardQuickLink> links = new ArrayList<>();
        links.add(new DashboardQuickLink("Startseite", "/?unit=" + unitId, null));
        if (moduleSettingsService.isEnabled(AppModule.PERSONAL, unitId)
                && userPermissionService.hasPermission(actor, unitId, "personal.read")) {
            links.add(new DashboardQuickLink("Personal", "/personal?unit=" + unitId, null));
        }
        if (moduleSettingsService.isEnabled(AppModule.TERMINE, unitId)
                && userPermissionService.hasPermission(actor, unitId, "termine.read")) {
            links.add(new DashboardQuickLink("Termine", "/termine?unit=" + unitId, null));
        }
        if (moduleSettingsService.isEnabled(AppModule.BERICHTE, unitId)
                && userPermissionService.hasPermission(actor, unitId, "berichte.read")) {
            links.add(new DashboardQuickLink("Berichte", "/berichte?unit=" + unitId, null));
        }
        if (moduleSettingsService.isEnabled(AppModule.RESERVIERUNGEN, unitId)
                && userPermissionService.hasPermission(actor, unitId, "reservierungen.read")) {
            links.add(new DashboardQuickLink(
                    "Reservierungen", "/reservierungen?unit=" + unitId, null));
        }
        if (moduleSettingsService.isEnabled(AppModule.ATEMSCHUTZ, unitId)
                && userPermissionService.hasPermission(actor, unitId, "atemschutz.read")) {
            links.add(new DashboardQuickLink("Atemschutz", "/atemschutz?unit=" + unitId, null));
        }
        if (moduleSettingsService.isEnabled(AppModule.AUSWERTUNG, unitId)
                && userPermissionService.hasPermission(actor, unitId, "auswertung.read")) {
            links.add(new DashboardQuickLink("Auswertung", "/auswertung?unit=" + unitId, null));
        }
        if (moduleSettingsService.isEnabled(AppModule.EINSATZAPP, unitId)
                && userPermissionService.hasPermission(actor, unitId, "einsatzapp.read")) {
            links.add(new DashboardQuickLink("Einsatz-App", "/settings/einsatzapp?unit=" + unitId, "Einstellungen"));
        }
        if (actor.getRole().isAdminLevel()) {
            links.add(new DashboardQuickLink("Administration", "/admin?scope=einheit&unit=" + unitId, null));
        }
        links.add(new DashboardQuickLink("Einstellungen", "/settings", null));
        return List.copyOf(links);
    }
}
