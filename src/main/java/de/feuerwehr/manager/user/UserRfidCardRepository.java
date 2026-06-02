package de.feuerwehr.manager.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRfidCardRepository extends JpaRepository<UserRfidCard, Long> {

    @Query("""
            SELECT c FROM UserRfidCard c
            JOIN FETCH c.user u
            WHERE c.cardUid = :cardUid AND c.active = TRUE AND u.active = TRUE
            """)
    Optional<UserRfidCard> findActiveCardWithUser(@Param("cardUid") String cardUid);

    boolean existsByCardUid(String cardUid);

    java.util.List<UserRfidCard> findByUserId(long userId);
}
