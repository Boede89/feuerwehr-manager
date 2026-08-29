package de.feuerwehr.manager.personal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseDetailPlanRepository extends JpaRepository<CourseDetailPlan, Long> {

    Optional<CourseDetailPlan> findByUnitIdAndPlanYearAndTestData(long unitId, int planYear, boolean testData);

    @Query("""
            SELECT DISTINCT p FROM CourseDetailPlan p
            LEFT JOIN FETCH p.items i
            LEFT JOIN FETCH i.course
            WHERE p.unit.id = :unitId AND p.planYear = :planYear AND p.testData = :testData
            """)
    Optional<CourseDetailPlan> findWithItemsByUnitIdAndPlanYearAndTestData(
            @Param("unitId") long unitId, @Param("planYear") int planYear, @Param("testData") boolean testData);

    @Query("SELECT p.planYear FROM CourseDetailPlan p WHERE p.unit.id = :unitId AND p.testData = :testData")
    List<Integer> findPlanYearsByUnitIdAndTestData(@Param("unitId") long unitId, @Param("testData") boolean testData);

    @Modifying
    @Query("DELETE FROM CourseDetailPlan p WHERE p.testData = true")
    void deleteAllByTestDataTrue();
}
