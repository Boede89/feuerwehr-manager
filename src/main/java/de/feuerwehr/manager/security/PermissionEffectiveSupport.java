package de.feuerwehr.manager.security;

import de.feuerwehr.manager.unit.UnitRolePermission;
import de.feuerwehr.manager.user.PermissionOverrideEffect;
import de.feuerwehr.manager.user.UserPermissionOverride;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reine Logik für effektive Modulrechte (testbar ohne Spring). */
public final class PermissionEffectiveSupport {

    private PermissionEffectiveSupport() {}

    /** {@code *.write} und {@code *.approve} implizieren {@code *.read}. */
    public static Set<String> expandImpliedReads(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>(UnitRolePermission.filterAllowed(List.copyOf(permissions)));
        LinkedHashSet<String> implied = new LinkedHashSet<>();
        for (String key : result) {
            int dot = key.lastIndexOf('.');
            if (dot <= 0) {
                continue;
            }
            String suffix = key.substring(dot + 1);
            if ("write".equals(suffix) || "approve".equals(suffix)) {
                implied.add(key.substring(0, dot) + ".read");
            }
        }
        result.addAll(UnitRolePermission.filterAllowed(List.copyOf(implied)));
        return result;
    }

    public static Set<String> applyOverrides(Set<String> base, List<UserPermissionOverride> overrides) {
        LinkedHashSet<String> result = new LinkedHashSet<>(base != null ? base : Set.of());
        if (overrides == null) {
            return result;
        }
        for (UserPermissionOverride override : overrides) {
            if (override == null || override.getPermission() == null || override.getPermission().isBlank()) {
                continue;
            }
            String permission = override.getPermission().trim();
            List<String> allowed = UnitRolePermission.filterAllowed(List.of(permission));
            if (allowed.isEmpty()) {
                continue;
            }
            String key = allowed.get(0);
            if (override.getEffect() == PermissionOverrideEffect.GRANT) {
                result.add(key);
            } else if (override.getEffect() == PermissionOverrideEffect.DENY) {
                result.remove(key);
            }
        }
        return expandImpliedReads(result);
    }
}
