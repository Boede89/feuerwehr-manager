package de.feuerwehr.manager.personal;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseDetailPlanEntryRepository extends JpaRepository<CourseDetailPlanEntry, Long> {

    @Query("""
            SELECT e FROM CourseDetailPlanEntry e
            JOIN FETCH e.person p
            JOIN FETCH e.item i
            WHERE i.id IN :itemIds
            ORDER BY e.sortOrder ASC, p.lastName ASC, p.firstName ASC
            """)
    List<CourseDetailPlanEntry> findByItemIdInWithPerson(@Param("itemIds") Collection<Long> itemIds);

    @Query("""
            SELECT e FROM CourseDetailPlanEntry e
            JOIN FETCH e.person
            JOIN FETCH e.item i
            JOIN FETCH i.plan plan
            WHERE e.id = :id
            """)
    java.util.Optional<CourseDetailPlanEntry> findByIdWithPlan(@Param("id") long id);
}
