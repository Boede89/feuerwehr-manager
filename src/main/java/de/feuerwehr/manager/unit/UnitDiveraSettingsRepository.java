package de.feuerwehr.manager.unit;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UnitDiveraSettingsRepository extends JpaRepository<UnitDiveraSettings, Long> {

    Optional<UnitDiveraSettings> findByUnitId(Long unitId);

    @Modifying
    @Query("DELETE FROM UnitDiveraSettings s WHERE s.unit.testData = true")
    void deleteAllByUnitTestDataTrue();
}
