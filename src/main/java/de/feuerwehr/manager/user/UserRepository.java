package de.feuerwehr.manager.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, long id);

    long count();

    List<User> findAllByAnonymizedAtIsNullOrderByUsernameAsc();

    long countByRoleAndActiveTrueAndAnonymizedAtIsNull(UserRole role);
}
