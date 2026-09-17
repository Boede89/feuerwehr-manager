package de.feuerwehr.manager.drivinglicense;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DrivingLicenseRepository extends JpaRepository<DrivingLicense, Long> {

    Optional<DrivingLicense> findByPersonId(long personId);

    @Query(
            """
            SELECT l FROM DrivingLicense l
            JOIN FETCH l.person p
            WHERE p.unit.id = :unitId
            """)
    java.util.List<DrivingLicense> findByUnitIdWithPerson(@Param("unitId") long unitId);
}
