package de.feuerwehr.manager.unit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserUnitFunctionRepository extends JpaRepository<UserUnitFunction, UserUnitFunctionId> {

    @Query("""
            SELECT uf FROM UserUnitFunction uf
            JOIN FETCH uf.role r
            WHERE uf.user.id = :userId
            ORDER BY r.name ASC
            """)
    List<UserUnitFunction> findByUserIdWithRoleOrderByRoleNameAsc(@Param("userId") long userId);

    boolean existsByUserIdAndRoleId(long userId, long roleId);

    void deleteByUserIdAndRoleId(long userId, long roleId);

    void deleteByUserId(long userId);
}
