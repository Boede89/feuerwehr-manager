package de.feuerwehr.manager.personal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonRepository extends JpaRepository<Person, Long> {

    @Query("""
            SELECT p FROM Person p
            LEFT JOIN FETCH p.unit
            LEFT JOIN FETCH p.qualificationType
            LEFT JOIN FETCH p.user
            WHERE p.unit.id = :unitId AND p.anonymizedAt IS NULL AND p.testData = :testData
            ORDER BY p.lastName, p.firstName
            """)
    List<Person> findActiveByUnitId(@Param("unitId") long unitId, @Param("testData") boolean testData);

    @Query("""
            SELECT p FROM Person p
            LEFT JOIN FETCH p.unit
            LEFT JOIN FETCH p.qualificationType
            LEFT JOIN FETCH p.user
            WHERE p.unit.id = :unitId AND p.anonymizedAt IS NULL AND p.testData = :testData
            ORDER BY p.lastName, p.firstName
            """)
    List<Person> findActiveByUnitIdWithUnit(@Param("unitId") long unitId, @Param("testData") boolean testData);

    @Query("""
            SELECT p FROM Person p
            LEFT JOIN FETCH p.unit
            LEFT JOIN FETCH p.qualificationType qt
            LEFT JOIN FETCH qt.dienstgradRole
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
            LEFT JOIN FETCH p.qualificationType
            LEFT JOIN FETCH p.user
            WHERE p.user.id = :userId AND p.unit.id = :unitId
              AND p.anonymizedAt IS NULL AND p.testData = :testData
            """)
    Optional<Person> findActiveByUserIdAndUnitId(
            @Param("userId") long userId, @Param("unitId") long unitId, @Param("testData") boolean testData);

    List<Person> findAllByUserIdAndAnonymizedAtIsNull(long userId);

    @Query("""
            SELECT p FROM Person p
            JOIN FETCH p.user u
            WHERE u.id IN :userIds
            ORDER BY p.id
            """)
    List<Person> findAllByUserIdIn(@Param("userIds") Collection<Long> userIds);

    @Query(
            """
            SELECT p FROM Person p
            LEFT JOIN FETCH p.user
            WHERE p.qualificationType.id = :qualificationTypeId AND p.anonymizedAt IS NULL
            """)
    List<Person> findByQualificationTypeId(@Param("qualificationTypeId") long qualificationTypeId);

    @Modifying
    @Query("DELETE FROM Person p WHERE p.testData = true")
    void deleteAllByTestDataTrue();

    @Query("""
            SELECT p FROM Person p
            WHERE p.unit.id = :unitId AND p.diveraUcrId = :diveraUcrId
              AND p.anonymizedAt IS NULL AND p.testData = :testData
            """)
    Optional<Person> findByUnitIdAndDiveraUcrId(
            @Param("unitId") long unitId,
            @Param("diveraUcrId") String diveraUcrId,
            @Param("testData") boolean testData);

    @Query("""
            SELECT p FROM Person p
            LEFT JOIN FETCH p.unit
            LEFT JOIN FETCH p.qualificationType
            WHERE p.diveraUcrId = :diveraUcrId
              AND p.anonymizedAt IS NULL AND p.testData = :testData
            ORDER BY p.unit.id
            """)
    List<Person> findAllByDiveraUcrId(
            @Param("diveraUcrId") String diveraUcrId, @Param("testData") boolean testData);

    @Query("""
            SELECT p FROM Person p
            LEFT JOIN FETCH p.unit
            LEFT JOIN FETCH p.qualificationType
            WHERE p.unit.id = :unitId AND p.anonymizedAt IS NULL AND p.testData = :testData
              AND (
                LOWER(p.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(p.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(CONCAT(p.lastName, ' ', p.firstName)) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            ORDER BY p.lastName, p.firstName
            """)
    List<Person> searchActiveByUnitId(
            @Param("unitId") long unitId, @Param("query") String query, @Param("testData") boolean testData);

    @Query("""
            SELECT p FROM Person p
            LEFT JOIN FETCH p.unit
            LEFT JOIN FETCH p.qualificationType
            WHERE p.id IN :ids AND p.anonymizedAt IS NULL AND p.testData = :testData
            """)
    List<Person> findActiveByIdIn(@Param("ids") Collection<Long> ids, @Param("testData") boolean testData);
}
