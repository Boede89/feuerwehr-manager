package de.feuerwehr.manager.security;

import de.feuerwehr.manager.unit.RolePermissionOption;
import de.feuerwehr.manager.unit.UnitRole;
import de.feuerwehr.manager.unit.UnitRolePermission;
import de.feuerwehr.manager.unit.UnitRoleService;
import de.feuerwehr.manager.unit.UserUnitFunction;
import de.feuerwehr.manager.unit.UserUnitFunctionRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.util.LinkedHashSet;
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
    private final UnitRoleService unitRoleService;

    @Transactional(readOnly = true)
    public Set<String> effectivePermissions(AppUserDetails actor, long unitId) {
        if (actor == null) {
            return Set.of();
        }
        if (actor.getRole().isAdminLevel()) {
            return allPermissionKeys();
        }
        User user =
                userRepository.findByIdWithUnit(actor.getUserId()).orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden"));
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        if (user.getUnit() != null && user.getUnit().getId().equals(unitId)) {
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
                    userUnitFunctionRepository.findByUserIdWithRoleOrderByRoleNameAsc(actor.getUserId())) {
                permissions.addAll(unitRoleService.parsePermissions(link.getRole()));
            }
        }
        return permissions;
    }

    public boolean hasPermission(AppUserDetails actor, long unitId, String permission) {
        if (actor != null && actor.getRole().isAdminLevel()) {
            return true;
        }
        return effectivePermissions(actor, unitId).contains(permission);
    }

    public void requirePermission(AppUserDetails actor, long unitId, String permission) {
        if (!hasPermission(actor, unitId, permission)) {
            throw new IllegalArgumentException("Keine Berechtigung für diese Aktion.");
        }
    }

    private static Set<String> allPermissionKeys() {
        return UnitRolePermission.permissionOptions().stream()
                .map(RolePermissionOption::value)
                .collect(Collectors.toUnmodifiableSet());
    }
}
