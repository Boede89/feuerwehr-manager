package de.feuerwehr.manager.berichte;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitBerichteEmailSettingsRepository extends JpaRepository<UnitBerichteEmailSettings, Long> {

    Optional<UnitBerichteEmailSettings> findByUnitIdAndReportType(long unitId, BerichteEmailReportType reportType);
}
