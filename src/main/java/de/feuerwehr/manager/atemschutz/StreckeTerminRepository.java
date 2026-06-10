package de.feuerwehr.manager.atemschutz;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StreckeTerminRepository extends JpaRepository<StreckeTermin, Long> {

    @Query("""
            SELECT t FROM StreckeTermin t
            WHERE t.unit.id = :unitId AND t.testData = :testData AND t.terminDatum >= :since
            ORDER BY t.terminDatum ASC, t.terminZeit ASC
            """)
    List<StreckeTermin> findRecentByUnit(
            @Param("unitId") long unitId, @Param("since") LocalDate since, @Param("testData") boolean testData);

    @Query("""
            SELECT t FROM StreckeTermin t
            WHERE t.id = :id AND t.unit.id = :unitId AND t.testData = :testData
            """)
    Optional<StreckeTermin> findByIdAndUnit(
            @Param("id") long id, @Param("unitId") long unitId, @Param("testData") boolean testData);

    @Modifying
    @Query("DELETE FROM StreckeTermin t WHERE t.testData = true")
    void deleteAllByTestDataTrue();
}
