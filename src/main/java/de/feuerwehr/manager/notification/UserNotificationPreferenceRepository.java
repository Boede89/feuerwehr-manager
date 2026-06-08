package de.feuerwehr.manager.notification;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationPreferenceRepository extends JpaRepository<UserNotificationPreference, Long> {

    List<UserNotificationPreference> findByUserId(long userId);

    Optional<UserNotificationPreference> findByUserIdAndTopicAndChannel(
            long userId, UserNotificationTopic topic, NotificationChannel channel);
}
