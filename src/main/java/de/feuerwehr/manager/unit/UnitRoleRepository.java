package de.feuerwehr.manager.unit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitRoleRepository extends JpaRepository<UnitRole, Long> {

    List<UnitRole> findByUnitIdOrderBySortOrderAscNameAsc(long unitId);

    Optional<UnitRole> findByIdAndUnitId(long id, long unitId);

    boolean existsByUnitIdAndNameAndIdNot(long unitId, String name, long excludeId);

    boolean existsByUnitIdAndName(long unitId, String name);

    long countByUnitIdAndRoleType(long unitId, UnitRoleType roleType);

    java.util.Optional<UnitRole> findByUnitIdAndName(long unitId, String name);
}
