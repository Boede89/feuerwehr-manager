package de.feuerwehr.manager.uvv;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitUvvSettingsRepository extends JpaRepository<UnitUvvSettings, Long> {

    Optional<UnitUvvSettings> findByUnitId(long unitId);
}
