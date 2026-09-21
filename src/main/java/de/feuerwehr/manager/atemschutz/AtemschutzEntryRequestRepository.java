package de.feuerwehr.manager.atemschutz;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AtemschutzEntryRequestRepository extends JpaRepository<AtemschutzEntryRequest, Long> {

    @Query(
            """
            SELECT DISTINCT r FROM AtemschutzEntryRequest r
            LEFT JOIN FETCH r.requestedBy
            LEFT JOIN FETCH r.carriers c
            LEFT JOIN FETCH c.person
            WHERE r.unit.id = :unitId
              AND r.status = :status
              AND r.testData = :testData
            """)
    List<AtemschutzEntryRequest> findByUnitIdAndStatus(
            @Param("unitId") long unitId,
            @Param("status") AtemschutzEntryRequestStatus status,
            @Param("testData") boolean testData);

    @Query(
            """
            SELECT COUNT(r) FROM AtemschutzEntryRequest r
            WHERE r.unit.id = :unitId
              AND r.status = de.feuerwehr.manager.atemschutz.AtemschutzEntryRequestStatus.PENDING
              AND r.testData = :testData
            """)
    long countPendingByUnitId(@Param("unitId") long unitId, @Param("testData") boolean testData);

    @Query(
            """
            SELECT DISTINCT r FROM AtemschutzEntryRequest r
            LEFT JOIN FETCH r.unit
            LEFT JOIN FETCH r.requestedBy
            LEFT JOIN FETCH r.reviewedBy
            LEFT JOIN FETCH r.carriers c
            LEFT JOIN FETCH c.person
            WHERE r.id = :id
            """)
    Optional<AtemschutzEntryRequest> findByIdWithDetails(@Param("id") long id);
}
