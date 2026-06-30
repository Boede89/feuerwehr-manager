package de.feuerwehr.manager.einsatzapp;

import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.Instant;
import java.util.List;
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

    private final UserRepository userRepository;
}
