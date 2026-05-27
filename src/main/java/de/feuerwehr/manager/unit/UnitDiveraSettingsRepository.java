package de.feuerwehr.manager.unit;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitDiveraSettingsRepository extends JpaRepository<UnitDiveraSettings, Long> {

    Optional<UnitDiveraSettings> findByUnitId(Long unitId);
}
