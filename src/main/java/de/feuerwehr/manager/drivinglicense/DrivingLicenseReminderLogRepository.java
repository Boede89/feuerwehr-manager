package de.feuerwehr.manager.drivinglicense;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrivingLicenseReminderLogRepository extends JpaRepository<DrivingLicenseReminderLog, Long> {

    Optional<DrivingLicenseReminderLog> findByLicenseIdAndMailKindAndNextDueOn(
            long licenseId, DrivingLicenseReminderMailKind mailKind, LocalDate nextDueOn);
}
