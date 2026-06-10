package de.feuerwehr.manager.atemschutz;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AtemschutzCarrierRepository extends JpaRepository<AtemschutzCarrier, Long> {

    @Query("""
            SELECT c FROM AtemschutzCarrier c
            JOIN FETCH c.person p
            LEFT JOIN FETCH p.user
            WHERE c.unit.id = :unitId AND c.testData = :testData
            ORDER BY p.lastName, p.firstName
            """)
    List<AtemschutzCarrier> findByUnitId(@Param("unitId") long unitId, @Param("testData") boolean testData);

    @Query("""
            SELECT c FROM AtemschutzCarrier c
            JOIN FETCH c.unit
            JOIN FETCH c.person p
            LEFT JOIN FETCH p.user
            WHERE c.id = :id AND c.testData = :testData
            """)
    Optional<AtemschutzCarrier> findByIdAndTestData(@Param("id") long id, @Param("testData") boolean testData);

    boolean existsByPersonId(long personId);

    @Query("""
            SELECT c FROM AtemschutzCarrier c
            JOIN FETCH c.person
            JOIN FETCH c.unit
            WHERE c.person.id = :personId AND c.testData = :testData
            """)
    Optional<AtemschutzCarrier> findByPersonIdAndTestData(
            @Param("personId") long personId, @Param("testData") boolean testData);

    @Query("""
            SELECT c.person.id FROM AtemschutzCarrier c
            WHERE c.unit.id = :unitId AND c.testData = :testData
            """)
    List<Long> findPersonIdsByUnitId(@Param("unitId") long unitId, @Param("testData") boolean testData);

    @Query("""
            SELECT c FROM AtemschutzCarrier c
            JOIN FETCH c.person
            WHERE c.productionSourceId = :sourceId
            """)
    Optional<AtemschutzCarrier> findShadowByProductionSourceId(@Param("sourceId") long sourceId);

    @Modifying
    @Query("DELETE FROM AtemschutzCarrier c WHERE c.testData = true")
    void deleteAllByTestDataTrue();
}
