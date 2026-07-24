package de.feuerwehr.manager.leitstellen;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UnitLeitstellenMailSettingsRepository extends JpaRepository<UnitLeitstellenMailSettings, Long> {

    Optional<UnitLeitstellenMailSettings> findByUnitId(long unitId);

    @Query("""
            SELECT s FROM UnitLeitstellenMailSettings s
            JOIN FETCH s.unit
            WHERE s.enabled = TRUE
            """)
    List<UnitLeitstellenMailSettings> findAllEnabled();
}
