package de.feuerwehr.manager.uvv;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UvvReminderLogRepository extends JpaRepository<UvvReminderLog, Long> {

    Optional<UvvReminderLog> findByPersonIdAndMailKindAndNextDueOn(
            long personId, UvvReminderMailKind mailKind, LocalDate nextDueOn);
}
