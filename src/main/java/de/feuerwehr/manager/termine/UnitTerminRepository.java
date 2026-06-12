package de.feuerwehr.manager.termine;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnitTerminRepository extends JpaRepository<UnitTermin, Long> {

    @Query("""
            SELECT t FROM UnitTermin t
            LEFT JOIN FETCH t.instructorPersons
            WHERE t.unit.id = :unitId AND t.category = :category
            ORDER BY t.startAt ASC, t.id ASC
            """)
    List<UnitTermin> findByUnitAndCategoryWithInstructor(
            @Param("unitId") long unitId, @Param("category") TermineCategory category);

    @Query("""
            SELECT DISTINCT t.title FROM UnitTermin t
            WHERE t.unit.id = :unitId AND t.category = :category
            ORDER BY t.title ASC
            """)
    List<String> findDistinctTitlesByUnitAndCategory(
            @Param("unitId") long unitId, @Param("category") TermineCategory category);
}
