package de.feuerwehr.manager.atemschutz;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AtemschutzReminderLogRepository extends JpaRepository<AtemschutzReminderLog, Long> {

    boolean existsByCarrierIdAndFitnessTypeAndMailKindAndValidUntil(
            long carrierId,
            AtemschutzFitnessType fitnessType,
            AtemschutzReminderMailKind mailKind,
            LocalDate validUntil);

    Optional<AtemschutzReminderLog> findByCarrierIdAndFitnessTypeAndMailKindAndValidUntil(
            long carrierId,
            AtemschutzFitnessType fitnessType,
            AtemschutzReminderMailKind mailKind,
            LocalDate validUntil);

    @Query("SELECT l FROM AtemschutzReminderLog l JOIN FETCH l.carrier WHERE l.carrier.id IN :ids")
    List<AtemschutzReminderLog> findByCarrier_IdIn(@Param("ids") Collection<Long> ids);
}
