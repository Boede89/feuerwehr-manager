package de.feuerwehr.manager.atemschutz;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StreckeZuordnungRepository extends JpaRepository<StreckeZuordnung, Long> {

    @Query("""
            SELECT z FROM StreckeZuordnung z
            JOIN FETCH z.carrier c
            JOIN FETCH c.person
            WHERE z.termin.id IN :terminIds
            ORDER BY c.person.lastName, c.person.firstName
            """)
    List<StreckeZuordnung> findByTerminIds(@Param("terminIds") List<Long> terminIds);

    default List<StreckeZuordnung> findByTerminIdsOrEmpty(List<Long> terminIds) {
        if (terminIds == null || terminIds.isEmpty()) {
            return List.of();
        }
        return findByTerminIds(terminIds);
    }

    @Query("""
            SELECT z FROM StreckeZuordnung z
            JOIN FETCH z.termin t
            WHERE z.carrier.id = :carrierId AND t.testData = :testData
            """)
    Optional<StreckeZuordnung> findByCarrierId(@Param("carrierId") long carrierId, @Param("testData") boolean testData);

    long countByTerminId(long terminId);

    @Modifying
    @Query("DELETE FROM StreckeZuordnung z WHERE z.carrier.id = :carrierId")
    void deleteByCarrierId(@Param("carrierId") long carrierId);

    @Modifying
    @Query("""
            DELETE FROM StreckeZuordnung z
            WHERE z.termin.id = :terminId AND z.carrier.id = :carrierId
            """)
    void deleteByTerminIdAndCarrierId(@Param("terminId") long terminId, @Param("carrierId") long carrierId);

    @Modifying
    @Query("""
            DELETE FROM StreckeZuordnung z
            WHERE z.termin.unit.id = :unitId AND z.termin.testData = :testData
            """)
    int deleteAllByUnit(@Param("unitId") long unitId, @Param("testData") boolean testData);
}
