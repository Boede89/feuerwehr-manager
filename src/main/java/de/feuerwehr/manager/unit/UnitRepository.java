package de.feuerwehr.manager.unit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    @Query("""
            SELECT u FROM Unit u
            WHERE u.active = TRUE AND (u.testData = FALSE OR :includeTestUnits = TRUE)
            ORDER BY u.name
            """)
    List<Unit> findActiveVisible(@Param("includeTestUnits") boolean includeTestUnits);

    @Query("""
            SELECT u FROM Unit u
            WHERE u.testData = FALSE OR :includeTestUnits = TRUE
            ORDER BY u.name
            """)
    List<Unit> findAllVisible(@Param("includeTestUnits") boolean includeTestUnits);

    @Query("""
            SELECT u FROM Unit u
            WHERE u.id = :id AND (u.testData = FALSE OR :includeTestUnits = TRUE)
            """)
    Optional<Unit> findVisibleById(@Param("id") long id, @Param("includeTestUnits") boolean includeTestUnits);

    boolean existsByNameIgnoreCaseAndTestData(String name, boolean testData);

    boolean existsByNameIgnoreCaseAndTestDataAndIdNot(String name, boolean testData, Long id);

    @Modifying
    @Query("DELETE FROM Unit u WHERE u.testData = true")
    void deleteAllByTestDataTrue();
}
