package de.feuerwehr.manager.unit;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitPrintSettingsRepository extends JpaRepository<UnitPrintSettings, Long> {

    Optional<UnitPrintSettings> findByUnitId(long unitId);
}
