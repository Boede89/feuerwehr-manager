package de.feuerwehr.manager.personal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonRepository extends JpaRepository<Person, Long> {

    @Query("""
            SELECT p FROM Person p
            LEFT JOIN FETCH p.qualificationType
            WHERE p.unit.id = :unitId AND p.anonymizedAt IS NULL
            ORDER BY p.lastName, p.firstName
            """)
    List<Person> findActiveByUnitId(@Param("unitId") long unitId);

    @Query("""
            SELECT p FROM Person p
            LEFT JOIN FETCH p.unit
            LEFT JOIN FETCH p.qualificationType
            LEFT JOIN FETCH p.user
            WHERE p.id = :id AND p.anonymizedAt IS NULL
            """)
    Optional<Person> findActiveById(@Param("id") long id);

    long countByUnitIdAndAnonymizedAtIsNull(long unitId);
}
