package de.feuerwehr.manager.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverride, Long> {

    List<UserPermissionOverride> findByUserIdOrderByPermissionAsc(long userId);

    Optional<UserPermissionOverride> findByUserIdAndPermission(long userId, String permission);

    void deleteByUserIdAndPermission(long userId, String permission);

    void deleteByUserId(long userId);
}