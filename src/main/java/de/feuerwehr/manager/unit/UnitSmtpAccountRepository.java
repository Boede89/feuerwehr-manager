package de.feuerwehr.manager.unit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitSmtpAccountRepository extends JpaRepository<UnitSmtpAccount, Long> {

    List<UnitSmtpAccount> findByUnitIdOrderBySortOrderAscLabelAsc(long unitId);

    Optional<UnitSmtpAccount> findByIdAndUnitId(long id, long unitId);
}
