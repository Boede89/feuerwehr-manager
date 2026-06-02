package de.feuerwehr.manager.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.unit
            WHERE LOWER(u.username) = LOWER(:username)
            """)
    Optional<User> findByUsernameIgnoreCaseWithUnit(@Param("username") String username);

    Optional<User> findByUsernameIgnoreCase(String username);

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.unit
            WHERE u.id = :id
            """)
    Optional<User> findByIdWithUnit(@Param("id") long id);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, long id);

    long count();

    List<User> findAllByAnonymizedAtIsNullOrderByUsernameAsc();

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.unit
            WHERE u.anonymizedAt IS NULL AND u.unit.id = :unitId
            ORDER BY u.username
            """)
    List<User> findAllByAnonymizedAtIsNullAndUnitIdOrderByUsernameAsc(@Param("unitId") long unitId);

    long countByRoleAndActiveTrueAndAnonymizedAtIsNull(UserRole role);
}
