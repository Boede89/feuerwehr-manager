package de.feuerwehr.manager.unit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnitRoleService {

    private final UnitRoleRepository unitRoleRepository;
    private final UnitRepository unitRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<UnitRole> listRoles(long unitId) {
        return unitRoleRepository.findByUnitIdOrderBySortOrderAscNameAsc(unitId);
    }

    @Transactional
    public UnitRole create(long unitId, String name, UnitRoleType type, List<String> permissions, Integer level) {
        Unit unit = unitRepository.findById(unitId).orElseThrow();
        String trimmed = normalizeName(name);
        if (unitRoleRepository.existsByUnitIdAndName(unitId, trimmed)) {
            throw new IllegalArgumentException("Eine Rolle mit diesem Namen existiert bereits.");
        }
        UnitRole role = new UnitRole();
        role.setUnit(unit);
        role.setName(trimmed);
        role.setRoleType(type != null ? type : UnitRoleType.DIENSTGRAD);
        role.setPermissionsJson(toJson(UnitRolePermission.filterAllowed(permissions)));
        role.setRoleLevel(type == UnitRoleType.FUNKTION ? null : level);
        return unitRoleRepository.save(role);
    }

    @Transactional
    public UnitRole update(
            long unitId, long roleId, String name, UnitRoleType type, List<String> permissions, Integer level) {
        UnitRole role = unitRoleRepository
                .findByIdAndUnitId(roleId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Rolle nicht gefunden."));
        String trimmed = normalizeName(name);
        if (unitRoleRepository.existsByUnitIdAndNameAndIdNot(unitId, trimmed, roleId)) {
            throw new IllegalArgumentException("Eine Rolle mit diesem Namen existiert bereits.");
        }
        role.setName(trimmed);
        if (type != null) {
            role.setRoleType(type);
        }
        role.setPermissionsJson(toJson(UnitRolePermission.filterAllowed(permissions)));
        role.setRoleLevel(role.getRoleType() == UnitRoleType.FUNKTION ? null : level);
        return unitRoleRepository.save(role);
    }

    @Transactional
    public void delete(long unitId, long roleId) {
        UnitRole role = unitRoleRepository
                .findByIdAndUnitId(roleId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Rolle nicht gefunden."));
        unitRoleRepository.delete(role);
    }

    public List<String> parsePermissions(UnitRole role) {
        try {
            return objectMapper.readValue(role.getPermissionsJson(), new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public String formatPermissionsLabel(UnitRole role) {
        return UnitRolePermission.formatPermissionsSummary(parsePermissions(role));
    }

    private String toJson(List<String> permissions) {
        try {
            return objectMapper.writeValueAsString(permissions);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bitte einen Rollennamen eingeben.");
        }
        return name.trim();
    }
}
