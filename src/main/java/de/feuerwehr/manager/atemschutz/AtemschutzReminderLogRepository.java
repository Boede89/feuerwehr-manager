package de.feuerwehr.manager.atemschutz;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AtemschutzReminderLogRepository extends JpaRepository<AtemschutzReminderLog, Long> {

    boolean existsByCarrierIdAndFitnessTypeAndMailKindAndValidUntil(
            long carrierId,
            AtemschutzFitnessType fitnessType,
            AtemschutzReminderMailKind mailKind,
            LocalDate validUntil);
}
