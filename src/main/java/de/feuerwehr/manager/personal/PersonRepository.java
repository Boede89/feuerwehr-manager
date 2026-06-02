package de.feuerwehr.manager.personal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonRepository extends JpaRepository<Person, Long> {

    @Query("""
            SELECT p FROM Person p
            LEFT JOIN FETCH p.qualificationType
            WHERE p.unit.id = :unitId AND p.anonymizedAt IS NULL AND p.testData = :testData
            ORDER BY p.lastName, p.firstName
            """)
    List<Person> findActiveByUnitId(@Param("unitId") long unitId, @Param("testData") boolean testData);

    @Query("""
            SELECT p FROM Person p
            LEFT JOIN FETCH p.unit
            LEFT JOIN FETCH p.qualificationType
            LEFT JOIN FETCH p.user
            WHERE p.id = :id AND p.anonymizedAt IS NULL AND p.testData = :testData
            """)
    Optional<Person> findActiveById(@Param("id") long id, @Param("testData") boolean testData);

    long countByUnitIdAndAnonymizedAtIsNullAndTestData(long unitId, boolean testData);

    @Query("""
            SELECT p FROM Person p
            LEFT JOIN FETCH p.unit
            LEFT JOIN FETCH p.qualificationType
            LEFT JOIN FETCH p.user
            WHERE p.productionSourceId = :sourceId AND p.anonymizedAt IS NULL
            """)
    Optional<Person> findShadowByProductionSourceId(@Param("sourceId") long sourceId);

    @Query("""
            SELECT p FROM Person p
            WHERE p.user.id = :userId AND p.unit.id = :unitId
              AND p.anonymizedAt IS NULL AND p.testData = :testData
            """)
    Optional<Person> findActiveByUserIdAndUnitId(
            @Param("userId") long userId, @Param("unitId") long unitId, @Param("testData") boolean testData);

    @Modifying
    @Query("DELETE FROM Person p WHERE p.testData = true")
    void deleteAllByTestDataTrue();
}
