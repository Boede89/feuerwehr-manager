package de.feuerwehr.manager.unit;

import de.feuerwehr.manager.settings.AppModule;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Modul-Berechtigungen für Einheits-Rollen (angelehnt an FW-Hub, Keys wie {@code personal.read}). */
public final class UnitRolePermission {

    private static final List<RoleModuleDefinition> MODULES = List.of(
            module(AppModule.PERSONAL, RolePermissionLevel.READ, RolePermissionLevel.WRITE),
            module(AppModule.RESERVIERUNGEN, RolePermissionLevel.READ, RolePermissionLevel.WRITE),
            module(AppModule.ATEMSCHUTZ, RolePermissionLevel.READ, RolePermissionLevel.WRITE),
            module(AppModule.BERICHTE, RolePermissionLevel.READ, RolePermissionLevel.WRITE, RolePermissionLevel.APPROVE),
            module(AppModule.TERMINE, RolePermissionLevel.READ, RolePermissionLevel.WRITE),
            module(AppModule.AUSWERTUNG, RolePermissionLevel.READ, RolePermissionLevel.WRITE));

    private static final Set<String> ALLOWED = MODULES.stream()
            .flatMap(m -> m.levels().stream().map(l -> l.permissionKey(m.key())))
            .collect(Collectors.toUnmodifiableSet());

    private static final Map<String, String> LABELS = buildLabels();

    private UnitRolePermission() {}

    public static List<RoleModuleDefinition> moduleDefinitions() {
        return MODULES;
    }

    public static Map<String, String> permissionLabels() {
        return LABELS;
    }

    /** Flache Liste für Thymeleaf (ohne Map-Zugriff mit dynamischen Keys). */
    /** Standard-Berechtigungen für die Systemrolle „Benutzer“. */
    public static List<String> defaultBenutzerPermissions() {
        return List.of("personal.read", "personal.write");
    }

    public static List<RolePermissionOption> permissionOptions() {
        return MODULES.stream()
                .flatMap(m -> m.levels().stream()
                        .map(l -> {
                            String key = l.permissionKey(m.key());
                            return new RolePermissionOption(key, LABELS.get(key));
                        }))
                .toList();
    }

    public static List<String> filterAllowed(List<String> permissions) {
        if (permissions == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : permissions) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String trimmed = raw.trim();
            if (ALLOWED.contains(trimmed)) {
                normalized.add(trimmed);
                continue;
            }
            expandLegacyKey(trimmed).forEach(normalized::add);
        }
        return List.copyOf(normalized);
    }

    public static String formatPermission(String key) {
        return LABELS.getOrDefault(key, key);
    }

    public static String formatPermissionsSummary(List<String> permissions) {
        List<String> allowed = filterAllowed(permissions);
        if (allowed.isEmpty()) {
            return "keine";
        }
        return allowed.stream().map(UnitRolePermission::formatPermission).collect(Collectors.joining(", "));
    }

    private static RoleModuleDefinition module(AppModule module, RolePermissionLevel... levels) {
        return new RoleModuleDefinition(
                module.key(),
                module.label() + (module.implemented() ? "" : " (demnächst)"),
                !module.implemented(),
                List.of(levels));
    }

    private static Map<String, String> buildLabels() {
        Map<String, String> map = new LinkedHashMap<>();
        for (RoleModuleDefinition module : MODULES) {
            for (RolePermissionLevel level : module.levels()) {
                String key = level.permissionKey(module.key());
                map.put(key, module.label().replace(" (demnächst)", "") + " (" + level.label() + ")");
            }
        }
        return map;
    }

    /** Alte Einträge ohne Stufe (z. B. {@code personal}) → Lesen + Schreiben. */
    private static List<String> expandLegacyKey(String key) {
        if (key.contains(".")) {
            return List.of();
        }
        return MODULES.stream()
                .filter(m -> m.key().equals(key))
                .findFirst()
                .map(m -> m.levels().stream().map(l -> l.permissionKey(m.key())).toList())
                .orElse(List.of());
    }
}
