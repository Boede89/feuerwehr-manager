package de.feuerwehr.manager.atemschutz;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitAtemschutzSettingsRepository extends JpaRepository<UnitAtemschutzSettings, Long> {

    Optional<UnitAtemschutzSettings> findByUnitId(long unitId);
}
