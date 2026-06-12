package de.feuerwehr.manager.personal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstructorGroupRepository extends JpaRepository<InstructorGroup, Long> {

    @Query("""
            SELECT DISTINCT g FROM InstructorGroup g
            LEFT JOIN FETCH g.members
            WHERE g.unit.id = :unitId AND g.testData = :testData
            ORDER BY g.thema
            """)
    List<InstructorGroup> findByUnitIdWithMembers(@Param("unitId") long unitId, @Param("testData") boolean testData);

    @Query("""
            SELECT g FROM InstructorGroup g
            LEFT JOIN FETCH g.unit
            LEFT JOIN FETCH g.members
            WHERE g.id = :id AND g.testData = :testData
            """)
    Optional<InstructorGroup> findByIdWithMembers(@Param("id") long id, @Param("testData") boolean testData);

    boolean existsByUnitIdAndThemaIgnoreCaseAndTestData(long unitId, String thema, boolean testData);

    boolean existsByUnitIdAndThemaIgnoreCaseAndTestDataAndIdNot(
            long unitId, String thema, boolean testData, long id);

    @Modifying
    @Query("DELETE FROM InstructorGroup g WHERE g.testData = true")
    void deleteAllByTestDataTrue();
}
