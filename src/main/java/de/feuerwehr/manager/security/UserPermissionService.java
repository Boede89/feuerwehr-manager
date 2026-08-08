package de.feuerwehr.manager.security;

import de.feuerwehr.manager.unit.RolePermissionOption;
import de.feuerwehr.manager.unit.UnitRole;
import de.feuerwehr.manager.unit.UnitRolePermission;
import de.feuerwehr.manager.unit.UnitRoleService;
import de.feuerwehr.manager.unit.UserUnitFunction;
import de.feuerwehr.manager.unit.UserUnitFunctionRepository;
import de.feuerwehr.manager.user.PermissionOverrideEffect;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserPermissionOverride;
import de.feuerwehr.manager.user.UserPermissionOverrideRepository;
import de.feuerwehr.manager.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPermissionService {

    private final UserRepository userRepository;
    private final UserUnitFunctionRepository userUnitFunctionRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final UnitRoleService unitRoleService;

    @Transactional(readOnly = true)
    public Set<String> effectivePermissions(AppUserDetails actor, long unitId) {
        if (actor == null) {
            return Set.of();
        }
        if (actor.getRole().isAdminLevel()) {
            return allPermissionKeys();
        }
        User user = userRepository.findByIdWithUnit(actor.getUserId()).orElse(null);
        if (user == null || user.getUnit() == null || !user.getUnit().getId().equals(unitId)) {
            return Set.of();
        }
        Set<String> base = basePermissionsForUser(user, unitId);
        List<UserPermissionOverride> overrides =
                overrideRepository.findByUserIdOrderByPermissionAsc(actor.getUserId());
        return PermissionEffectiveSupport.applyOverrides(base, overrides);
    }

    /** Rechte nur aus Dienstgrad + Zusatzfunktionen (ohne Overrides), inkl. read-Implikation. */
    @Transactional(readOnly = true)
    public Set<String> basePermissions(AppUserDetails actor, long unitId) {
        if (actor == null) {
            return Set.of();
        }
        if (actor.getRole().isAdminLevel()) {
            return allPermissionKeys();
        }
        User user = userRepository.findByIdWithUnit(actor.getUserId()).orElse(null);
        if (user == null || user.getUnit() == null || !user.getUnit().getId().equals(unitId)) {
            return Set.of();
        }
        return basePermissionsForUser(user, unitId);
    }

    @Transactional(readOnly = true)
    public Set<String> basePermissionsForUser(long userId, long unitId) {
        User user = userRepository.findByIdWithUnit(userId).orElse(null);
        if (user == null) {
            return Set.of();
        }
        if (user.getRole().isAdminLevel()) {
            return allPermissionKeys();
        }
        if (user.getUnit() == null || !user.getUnit().getId().equals(unitId)) {
            return Set.of();
        }
        return basePermissionsForUser(user, unitId);
    }

    @Transactional(readOnly = true)
    public List<UserPermissionOverride> listOverrides(long userId) {
        return overrideRepository.findByUserIdOrderByPermissionAsc(userId);
    }

    @Transactional(readOnly = true)
    public Map<String, PermissionOverrideEffect> overrideMap(long userId) {
        Map<String, PermissionOverrideEffect> map = new LinkedHashMap<>();
        for (UserPermissionOverride row : listOverrides(userId)) {
            map.put(row.getPermission(), row.getEffect());
        }
        return map;
    }

    public boolean hasPermission(AppUserDetails actor, long unitId, String permission) {
        if (actor != null && actor.getRole().isAdminLevel()) {
            return true;
        }
        try {
            return effectivePermissions(actor, unitId).contains(permission);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Modul sichtbar/nutzbar, wenn Lesen, Schreiben oder Genehmigen vorhanden ist. */
    public boolean hasModuleAccess(AppUserDetails actor, long unitId, String moduleKey) {
        if (actor != null && actor.getRole().isAdminLevel()) {
            return true;
        }
        try {
            Set<String> effective = effectivePermissions(actor, unitId);
            return effective.contains(moduleKey + ".read")
                    || effective.contains(moduleKey + ".write")
                    || effective.contains(moduleKey + ".approve");
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void requirePermission(AppUserDetails actor, long unitId, String permission) {
        if (!hasPermission(actor, unitId, permission)) {
            throw new IllegalArgumentException("Keine Berechtigung für diese Aktion.");
        }
    }

    private Set<String> basePermissionsForUser(User user, long unitId) {
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        UnitRole orgRole = user.getOrganizationalRole();
        if (orgRole != null) {
            permissions.addAll(unitRoleService.parsePermissions(orgRole));
        } else {
            unitRoleService.listRoles(unitId).stream()
                    .filter(UnitRole::isSystemRole)
                    .findFirst()
                    .ifPresent(role -> permissions.addAll(unitRoleService.parsePermissions(role)));
        }
        for (UserUnitFunction link :
                userUnitFunctionRepository.findByUserIdWithRoleOrderByRoleNameAsc(user.getId())) {
            permissions.addAll(unitRoleService.parsePermissions(link.getRole()));
        }
        return PermissionEffectiveSupport.expandImpliedReads(permissions);
    }

    private static Set<String> allPermissionKeys() {
        return UnitRolePermission.permissionOptions().stream()
                .map(RolePermissionOption::value)
                .collect(Collectors.toUnmodifiableSet());
    }
}
