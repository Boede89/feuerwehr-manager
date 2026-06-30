package de.feuerwehr.manager.einsatzapp;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitEinsatzappSettingsRepository extends JpaRepository<UnitEinsatzappSettings, Long> {

    Optional<UnitEinsatzappSettings> findByUnitId(long unitId);
}
