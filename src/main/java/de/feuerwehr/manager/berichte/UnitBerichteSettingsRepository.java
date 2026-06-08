package de.feuerwehr.manager.berichte;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitBerichteSettingsRepository extends JpaRepository<UnitBerichteSettings, Long> {

    Optional<UnitBerichteSettings> findByUnitId(long unitId);
}
