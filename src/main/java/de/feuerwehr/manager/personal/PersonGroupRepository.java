package de.feuerwehr.manager.personal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonGroupRepository extends JpaRepository<PersonGroup, Long> {

    @Query("""
            SELECT DISTINCT g FROM PersonGroup g
            LEFT JOIN FETCH g.members m
            LEFT JOIN FETCH m.unit
            WHERE g.unit.id = :unitId AND g.testData = :testData
            ORDER BY g.name
            """)
    List<PersonGroup> findByUnitIdWithMembers(@Param("unitId") long unitId, @Param("testData") boolean testData);

    @Query("""
            SELECT g FROM PersonGroup g
            LEFT JOIN FETCH g.unit
            LEFT JOIN FETCH g.members m
            LEFT JOIN FETCH m.unit
            WHERE g.id = :id AND g.testData = :testData
            """)
    Optional<PersonGroup> findByIdWithMembers(@Param("id") long id, @Param("testData") boolean testData);

    boolean existsByUnitIdAndNameIgnoreCaseAndTestData(long unitId, String name, boolean testData);

    boolean existsByUnitIdAndNameIgnoreCaseAndTestDataAndIdNot(
            long unitId, String name, boolean testData, long id);

    @Query("""
            SELECT g.id FROM PersonGroup g
            JOIN g.members m
            WHERE m.id = :personId
            """)
    java.util.Set<Long> findGroupIdsByMemberId(@Param("personId") long personId);
}
