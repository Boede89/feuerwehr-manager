package de.feuerwehr.manager.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
            LEFT JOIN FETCH u.organizationalRole
            WHERE u.id = :id
            """)
    Optional<User> findByIdWithUnit(@Param("id") long id);

    long countByAnonymizedAtIsNull();

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, long id);

    long count();

    List<User> findAllByAnonymizedAtIsNullOrderByUsernameAsc();

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.unit
            WHERE u.anonymizedAt IS NULL
            ORDER BY u.username
            """)
    List<User> findAllByAnonymizedAtIsNullWithUnitOrderByUsernameAsc();

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.unit
            WHERE LOWER(u.loginEmail) = LOWER(:email)
            """)
    Optional<User> findByLoginEmailIgnoreCaseWithUnit(@Param("email") String email);

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.unit
            WHERE u.anonymizedAt IS NULL
              AND u.id IN (
                  SELECT p.user.id FROM Person p
                  WHERE p.anonymizedAt IS NULL
                    AND p.user IS NOT NULL
                    AND LOWER(p.email) = LOWER(:email)
              )
            """)
    Optional<User> findByPersonEmailIgnoreCaseWithUnit(@Param("email") String email);

    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.loginEmail) = LOWER(:email)
              AND u.anonymizedAt IS NULL
              AND (:excludeId IS NULL OR u.id <> :excludeId)
            """)
    Optional<User> findByLoginEmailIgnoreCaseExcludingId(
            @Param("email") String email, @Param("excludeId") Long excludeId);

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.unit
            LEFT JOIN FETCH u.organizationalRole
            WHERE u.anonymizedAt IS NULL AND u.unit.id = :unitId
            ORDER BY u.username
            """)
    List<User> findAllByAnonymizedAtIsNullAndUnitIdOrderByUsernameAsc(@Param("unitId") long unitId);

    /** Einheits-Benutzerliste inkl. dieser Einheit zugeordneter Superadmins (nur Anzeige für Einheitsadmin). */
    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.unit
            LEFT JOIN FETCH u.organizationalRole
            WHERE u.anonymizedAt IS NULL
              AND u.unit.id = :unitId
              AND u.role IN ('USER', 'UNIT_ADMIN', 'SUPER_ADMIN')
            ORDER BY u.username
            """)
    List<User> findUnitScopedAccountsByUnitId(@Param("unitId") long unitId);

    long countByRoleAndActiveTrueAndAnonymizedAtIsNull(UserRole role);

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.unit
            WHERE u.anonymizedAt IS NULL
              AND u.role IN ('SUPER_ADMIN', 'UNIT_ADMIN')
            ORDER BY u.role, u.username
            """)
    List<User> findAdminLevelAccountsWithUnit();

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.unit
            WHERE u.anonymizedAt IS NULL
              AND u.role = 'UNIT_ADMIN'
              AND u.unit.id = :unitId
            ORDER BY u.username
            """)
    List<User> findUnitAdminsByUnitId(@Param("unitId") long unitId);

    @Modifying
    @Query("UPDATE User u SET u.organizationalRole = null WHERE u.organizationalRole IS NOT NULL AND u.organizationalRole.unit.id = :unitId")
    void clearOrganizationalRolesByUnitId(@Param("unitId") long unitId);

    @Modifying
    @Query("UPDATE User u SET u.unit = null WHERE u.unit.id = :unitId")
    void clearUnitAssignmentByUnitId(@Param("unitId") long unitId);
}
