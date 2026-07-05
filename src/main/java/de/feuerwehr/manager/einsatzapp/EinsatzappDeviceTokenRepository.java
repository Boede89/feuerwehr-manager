package de.feuerwehr.manager.einsatzapp;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EinsatzappDeviceTokenRepository extends JpaRepository<EinsatzappDeviceToken, Long> {

    List<EinsatzappDeviceToken> findByUnitId(long unitId);

    @Query("""
            SELECT t FROM EinsatzappDeviceToken t
            JOIN FETCH t.user u
            WHERE t.unit.id = :unitId
            ORDER BY u.displayName ASC, u.username ASC, t.lastSeenAt DESC
            """)
    List<EinsatzappDeviceToken> findByUnitIdWithUserOrderByUserAndLastSeen(@Param("unitId") long unitId);

    List<EinsatzappDeviceToken> findByUserIdAndUnitId(long userId, long unitId);

    Optional<EinsatzappDeviceToken> findByUserIdAndFcmToken(long userId, String fcmToken);

    long countByUnitId(long unitId);

    @Modifying
    @Query("DELETE FROM EinsatzappDeviceToken t WHERE t.fcmToken IN :tokens")
    int deleteByFcmTokenIn(@Param("tokens") Collection<String> tokens);

    @Modifying
    @Query("DELETE FROM EinsatzappDeviceToken t WHERE t.user.id = :userId AND t.fcmToken = :token")
    int deleteByUserIdAndFcmToken(@Param("userId") long userId, @Param("token") String token);
}
