package de.feuerwehr.manager.atemschutz;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AtemschutzFitnessRecordRepository extends JpaRepository<AtemschutzFitnessRecord, Long> {

    @Query("""
            SELECT r FROM AtemschutzFitnessRecord r
            JOIN FETCH r.carrier
            WHERE r.carrier.id = :carrierId AND r.testData = :testData
            ORDER BY r.validUntil DESC, r.id DESC
            """)
    List<AtemschutzFitnessRecord> findByCarrierId(@Param("carrierId") long carrierId, @Param("testData") boolean testData);

    @Query("""
            SELECT r FROM AtemschutzFitnessRecord r
            JOIN FETCH r.carrier c
            JOIN FETCH c.person
            WHERE r.id = :id AND r.testData = :testData
            """)
    Optional<AtemschutzFitnessRecord> findByIdAndTestData(@Param("id") long id, @Param("testData") boolean testData);

    @Query("""
            SELECT r FROM AtemschutzFitnessRecord r
            WHERE r.carrier.id IN :carrierIds AND r.recordType = :type AND r.testData = :testData
            ORDER BY r.validUntil DESC
            """)
    List<AtemschutzFitnessRecord> findByCarrierIdsAndType(
            @Param("carrierIds") List<Long> carrierIds,
            @Param("type") AtemschutzFitnessType type,
            @Param("testData") boolean testData);
}
