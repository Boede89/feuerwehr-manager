package de.feuerwehr.manager.termine;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    @Query("""
            SELECT t FROM UnitTermin t
            WHERE t.id = :id AND t.unit.id = :unitId
            """)
    Optional<UnitTermin> findByIdAndUnitId(@Param("id") long id, @Param("unitId") long unitId);

    @Query("""
            SELECT DISTINCT t FROM UnitTermin t
            LEFT JOIN FETCH t.instructorPersons
            WHERE t.unit.id = :unitId
            AND (
                t.audienceAll = true
                OR EXISTS (SELECT 1 FROM t.assignedPersons ap WHERE ap.id = :personId)
                OR EXISTS (SELECT 1 FROM t.assignedGroups ag JOIN ag.members m WHERE m.id = :personId)
            )
            ORDER BY t.startAt ASC, t.id ASC
            """)
    List<UnitTermin> findMineByUnitAndPerson(@Param("unitId") long unitId, @Param("personId") long personId);

    @Query("""
            SELECT t FROM UnitTermin t
            WHERE t.unit.id = :unitId AND t.category IN :categories
            ORDER BY t.startAt ASC, t.id ASC
            """)
    List<UnitTermin> findByUnitIdAndCategoryIn(
            @Param("unitId") long unitId, @Param("categories") Collection<TermineCategory> categories);
}
