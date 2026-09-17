package de.feuerwehr.manager.drivinglicense;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitDrivingLicenseSettingsRepository extends JpaRepository<UnitDrivingLicenseSettings, Long> {

    Optional<UnitDrivingLicenseSettings> findByUnitId(long unitId);
}
