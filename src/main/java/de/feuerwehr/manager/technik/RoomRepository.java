package de.feuerwehr.manager.technik;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(long unitId, boolean testData);

    Optional<Room> findByIdAndUnitId(long id, long unitId);

    @Query("SELECT r FROM Room r WHERE r.productionSourceId = :sourceId")
    Optional<Room> findShadowByProductionSourceId(@Param("sourceId") long sourceId);

    @Modifying
    @Query("DELETE FROM Room r WHERE r.testData = true")
    void deleteAllByTestDataTrue();
}
