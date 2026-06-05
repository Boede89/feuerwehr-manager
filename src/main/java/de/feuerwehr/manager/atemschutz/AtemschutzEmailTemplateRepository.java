package de.feuerwehr.manager.atemschutz;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AtemschutzEmailTemplateRepository extends JpaRepository<AtemschutzEmailTemplate, Long> {

    List<AtemschutzEmailTemplate> findByUnitIdOrderByTemplateKeyAsc(long unitId);

    Optional<AtemschutzEmailTemplate> findByUnitIdAndTemplateKey(long unitId, String templateKey);
}
