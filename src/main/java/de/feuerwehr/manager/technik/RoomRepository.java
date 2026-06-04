package de.feuerwehr.manager.technik;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(long unitId, boolean testData);

    Optional<Room> findByIdAndUnitId(long id, long unitId);
}
