package de.feuerwehr.manager.notification;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserNotificationPreferenceService {

    private final UserNotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserNotificationTopicView> buildSettingsView(long userId) {
        Map<UserNotificationTopic, Map<NotificationChannel, Boolean>> stored = loadStored(userId);
        List<UserNotificationTopicView> views = new ArrayList<>();
        for (UserNotificationTopic topic : UserNotificationTopic.values()) {
            Map<NotificationChannel, Boolean> channels = new EnumMap<>(NotificationChannel.class);
            for (NotificationChannel channel : NotificationChannel.values()) {
                channels.put(channel, stored.getOrDefault(topic, Map.of()).getOrDefault(channel, true));
            }
            views.add(new UserNotificationTopicView(topic, channels));
        }
        return views;
    }

    @Transactional
    public void saveEmailPreferences(long userId, Map<UserNotificationTopic, Boolean> emailEnabledByTopic) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden."));
        for (UserNotificationTopic topic : UserNotificationTopic.values()) {
            boolean enabled = emailEnabledByTopic.getOrDefault(topic, true);
            savePreference(user, topic, NotificationChannel.EMAIL, enabled);
        }
    }

    @Transactional(readOnly = true)
    public boolean isEmailEnabled(long userId, UserNotificationTopic topic) {
        return preferenceRepository
                .findByUserIdAndTopicAndChannel(userId, topic, NotificationChannel.EMAIL)
                .map(UserNotificationPreference::isEnabled)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean isEmailEnabledForPerson(Person person, UserNotificationTopic topic) {
        if (person == null) {
            return false;
        }
        User user = person.getUser();
        if (user != null) {
            return isEmailEnabled(user.getId(), topic);
        }
        return true;
    }

    @Transactional(readOnly = true)
    public boolean isEmailEnabledForUser(User user, UserNotificationTopic topic) {
        if (user == null) {
            return false;
        }
        return isEmailEnabled(user.getId(), topic);
    }

    private void savePreference(
            User user, UserNotificationTopic topic, NotificationChannel channel, boolean enabled) {
        UserNotificationPreference pref = preferenceRepository
                .findByUserIdAndTopicAndChannel(user.getId(), topic, channel)
                .orElseGet(() -> {
                    UserNotificationPreference created = new UserNotificationPreference();
                    created.setUser(user);
                    created.setTopic(topic);
                    created.setChannel(channel);
                    return created;
                });
        pref.setEnabled(enabled);
        preferenceRepository.save(pref);
    }

    private Map<UserNotificationTopic, Map<NotificationChannel, Boolean>> loadStored(long userId) {
        Map<UserNotificationTopic, Map<NotificationChannel, Boolean>> result = new EnumMap<>(UserNotificationTopic.class);
        for (UserNotificationPreference pref : preferenceRepository.findByUserId(userId)) {
            result.computeIfAbsent(pref.getTopic(), ignored -> new EnumMap<>(NotificationChannel.class))
                    .put(pref.getChannel(), pref.isEnabled());
        }
        return result;
    }

    public record UserNotificationTopicView(
            UserNotificationTopic topic, Map<NotificationChannel, Boolean> channels) {}
}
