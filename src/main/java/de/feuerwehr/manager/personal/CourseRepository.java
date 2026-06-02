package de.feuerwehr.manager.personal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {

    @Query("""
            SELECT c FROM Course c
            LEFT JOIN FETCH c.qualificationType
            WHERE c.unit.id = :unitId AND c.active = TRUE AND c.testData = :testData
            ORDER BY c.name
            """)
    List<Course> findActiveByUnitId(@Param("unitId") long unitId, @Param("testData") boolean testData);

    List<Course> findByUnitIdAndTestDataOrderByNameAsc(long unitId, boolean testData);

    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.qualificationType WHERE c.productionSourceId = :sourceId")
    Optional<Course> findShadowByProductionSourceId(@Param("sourceId") long sourceId);

    @Modifying
    @Query("DELETE FROM Course c WHERE c.testData = true")
    void deleteAllByTestDataTrue();
}
