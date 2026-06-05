package de.feuerwehr.manager.atemschutz;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnitAtemschutzSettingsRepository extends JpaRepository<UnitAtemschutzSettings, Long> {

    @Query("""
            SELECT s FROM UnitAtemschutzSettings s
            LEFT JOIN FETCH s.agtCourse
            WHERE s.unitId = :unitId
            """)
    Optional<UnitAtemschutzSettings> findByUnitId(@Param("unitId") long unitId);
}
