package de.feuerwehr.manager.personal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseDetailPlanItemRepository extends JpaRepository<CourseDetailPlanItem, Long> {

    @Query("""
            SELECT i FROM CourseDetailPlanItem i
            JOIN FETCH i.plan p
            JOIN FETCH i.course
            WHERE i.id = :id
            """)
    Optional<CourseDetailPlanItem> findByIdWithPlan(@Param("id") long id);
}
