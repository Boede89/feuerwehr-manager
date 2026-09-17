package de.feuerwehr.manager.drivinglicense;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DrivingLicenseCheckRepository extends JpaRepository<DrivingLicenseCheck, Long> {

    @Query(
            """
            SELECT c FROM DrivingLicenseCheck c
            LEFT JOIN FETCH c.checkedBy
            WHERE c.license.id = :licenseId
            ORDER BY c.checkedOn DESC, c.id DESC
            """)
    List<DrivingLicenseCheck> findByLicenseIdOrderByCheckedOnDesc(@Param("licenseId") long licenseId);
}
