package de.feuerwehr.manager.unit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitCalendarAccountRepository extends JpaRepository<UnitCalendarAccount, Long> {

    List<UnitCalendarAccount> findByUnitIdOrderBySortOrderAscLabelAsc(long unitId);

    Optional<UnitCalendarAccount> findByIdAndUnitId(long id, long unitId);
}
