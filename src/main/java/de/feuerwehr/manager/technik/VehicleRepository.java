package de.feuerwehr.manager.technik;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(long unitId, boolean testData);

    Optional<Vehicle> findByIdAndUnitId(long id, long unitId);

    long countByUnitIdAndVehicleType(long unitId, String vehicleType);

    @Query("SELECT v FROM Vehicle v WHERE v.productionSourceId = :sourceId")
    Optional<Vehicle> findShadowByProductionSourceId(@Param("sourceId") long sourceId);

    @Modifying
    @Query("DELETE FROM Vehicle v WHERE v.testData = true")
    void deleteAllByTestDataTrue();
}
