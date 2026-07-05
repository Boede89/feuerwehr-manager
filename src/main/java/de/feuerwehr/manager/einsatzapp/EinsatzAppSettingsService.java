package de.feuerwehr.manager.einsatzapp;

import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EinsatzAppSettingsService {

    private final UnitEinsatzappSettingsRepository settingsRepository;
    private final UnitRepository unitRepository;
    private final EinsatzappDeviceTokenRepository deviceTokenRepository;
    private final EinsatzappPushLogRepository pushLogRepository;
    private final FcmConfigService fcmConfigService;
    private final UserPermissionService userPermissionService;

    @Transactional
    public UnitEinsatzappSettings ensureSettings(long unitId) {
        return settingsRepository
                .findByUnitId(unitId)
                .orElseGet(() -> createDefaults(unitId));
    }

    @Transactional(readOnly = true)
    public boolean isPushEnabled(long unitId) {
        return settingsRepository.findByUnitId(unitId).map(UnitEinsatzappSettings::isPushEnabled).orElse(false);
    }

    @Transactional
    public UnitEinsatzappSettings savePushEnabled(long unitId, boolean pushEnabled) {
        UnitEinsatzappSettings settings = ensureSettings(unitId);
        settings.setPushEnabled(pushEnabled);
        return settingsRepository.save(settings);
    }

    @Transactional(readOnly = true)
    public long countDevices(long unitId) {
        return deviceTokenRepository.countByUnitId(unitId);
    }

    @Transactional(readOnly = true)
    public List<EinsatzappPushLog> recentPushLog(long unitId) {
        return pushLogRepository.findTop10ByUnitIdOrderByCreatedAtDesc(unitId);
    }

    @Transactional(readOnly = true)
    public List<EinsatzappRegisteredUserRow> listRegisteredUsers(long unitId) {
        Map<Long, AggregatedUserDevices> byUser = new LinkedHashMap<>();
        for (EinsatzappDeviceToken token : deviceTokenRepository.findByUnitIdWithUserOrderByUserAndLastSeen(unitId)) {
            User user = token.getUser();
            if (user == null || user.getAnonymizedAt() != null) {
                continue;
            }
            AggregatedUserDevices agg =
                    byUser.computeIfAbsent(user.getId(), id -> new AggregatedUserDevices(user));
            agg.add(token);
        }
        List<EinsatzappRegisteredUserRow> rows = new ArrayList<>();
        for (AggregatedUserDevices agg : byUser.values()) {
            User user = agg.user();
            boolean pushEligible =
                    userPermissionService.hasPermission(AppUserDetails.from(user), unitId, "einsatzapp.read");
            rows.add(new EinsatzappRegisteredUserRow(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    agg.deviceCount(),
                    agg.deviceSummary(),
                    agg.firstRegisteredAt(),
                    agg.lastSeenAt(),
                    pushEligible));
        }
        rows.sort((a, b) -> {
            int byName = a.displayName().compareToIgnoreCase(b.displayName());
            if (byName != 0) {
                return byName;
            }
            return a.username().compareToIgnoreCase(b.username());
        });
        return rows;
    }

    @Transactional(readOnly = true)
    public boolean isFcmConfigured() {
        return fcmConfigService.isConfigured();
    }

    @Transactional
    public EinsatzappDeviceToken registerDevice(
            long userId, long unitId, String fcmToken, String deviceLabel, String platform) {
        if (fcmToken == null || fcmToken.isBlank()) {
            throw new IllegalArgumentException("FCM-Token fehlt.");
        }
        String normalizedToken = fcmToken.trim();
        if (normalizedToken.length() > 512) {
            throw new IllegalArgumentException("FCM-Token ist zu lang.");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden."));
        Unit unit = unitRepository.findById(unitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        EinsatzappDeviceToken row = deviceTokenRepository
                .findByUserIdAndFcmToken(userId, normalizedToken)
                .orElseGet(EinsatzappDeviceToken::new);
        row.setUser(user);
        row.setUnit(unit);
        row.setFcmToken(normalizedToken);
        row.setDeviceLabel(blankToNull(deviceLabel));
        row.setPlatform(platform != null && !platform.isBlank() ? platform.trim() : "android");
        row.setLastSeenAt(Instant.now());
        return deviceTokenRepository.save(row);
    }

    @Transactional
    public void unregisterDevice(long userId, String fcmToken) {
        if (fcmToken == null || fcmToken.isBlank()) {
            return;
        }
        deviceTokenRepository.deleteByUserIdAndFcmToken(userId, fcmToken.trim());
    }

    @Transactional(readOnly = true)
    public List<EinsatzappDeviceToken> listDevicesForUser(long userId, long unitId) {
        return deviceTokenRepository.findByUserIdAndUnitId(userId, unitId);
    }

    private UnitEinsatzappSettings createDefaults(long unitId) {
        Unit unit = unitRepository.findById(unitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        UnitEinsatzappSettings settings = new UnitEinsatzappSettings();
        settings.setUnit(unit);
        settings.setPushEnabled(false);
        return settingsRepository.save(settings);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static final class AggregatedUserDevices {
        private final User user;
        private int deviceCount;
        private final List<String> labels = new ArrayList<>();
        private Instant firstRegisteredAt;
        private Instant lastSeenAt;

        private AggregatedUserDevices(User user) {
            this.user = user;
        }

        private User user() {
            return user;
        }

        private void add(EinsatzappDeviceToken token) {
            deviceCount++;
            String label = token.getDeviceLabel();
            if (label != null && !label.isBlank()) {
                labels.add(label.trim());
            } else if (token.getPlatform() != null && !token.getPlatform().isBlank()) {
                labels.add(token.getPlatform().trim());
            }
            Instant created = token.getCreatedAt();
            if (created != null && (firstRegisteredAt == null || created.isBefore(firstRegisteredAt))) {
                firstRegisteredAt = created;
            }
            Instant seen = token.getLastSeenAt();
            if (seen != null && (lastSeenAt == null || seen.isAfter(lastSeenAt))) {
                lastSeenAt = seen;
            }
        }

        private int deviceCount() {
            return deviceCount;
        }

        private String deviceSummary() {
            if (labels.isEmpty()) {
                return deviceCount == 1 ? "1 Gerät" : deviceCount + " Geräte";
            }
            return String.join(", ", labels);
        }

        private Instant firstRegisteredAt() {
            return firstRegisteredAt;
        }

        private Instant lastSeenAt() {
            return lastSeenAt;
        }
    }

    private final UserRepository userRepository;
}
