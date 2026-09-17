package de.feuerwehr.manager.drivinglicense;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DrivingLicenseSettingsService {

    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {};

    private final UnitRepository unitRepository;
    private final UnitDrivingLicenseSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public UnitDrivingLicenseSettings ensureSettings(long unitId) {
        return settingsRepository.findByUnitId(unitId).orElseGet(() -> {
            Unit unit = unitRepository
                    .findById(unitId)
                    .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
            UnitDrivingLicenseSettings settings = new UnitDrivingLicenseSettings();
            settings.setUnit(unit);
            settings.setIntervalMonths(12);
            settings.setWarnDays(30);
            settings.setNotifyPerson(false);
            return settingsRepository.save(settings);
        });
    }

    @Transactional
    public UnitDrivingLicenseSettings save(
            long unitId, int intervalMonths, int warnDays, boolean notifyPerson, Long[] notificationUserIds) {
        if (intervalMonths < 1 || intervalMonths > 36) {
            throw new IllegalArgumentException("Das Kontrollintervall muss zwischen 1 und 36 Monaten liegen.");
        }
        if (warnDays < 0 || warnDays > 365) {
            throw new IllegalArgumentException("Die Warnzeit muss zwischen 0 und 365 Tagen liegen.");
        }
        UnitDrivingLicenseSettings settings = ensureSettings(unitId);
        settings.setIntervalMonths(intervalMonths);
        settings.setWarnDays(warnDays);
        settings.setNotifyPerson(notifyPerson);
        settings.setNotificationUserIds(toJson(notificationUserIds));
        return settingsRepository.save(settings);
    }

    @Transactional(readOnly = true)
    public List<User> listSelectableUnitUsers(long unitId) {
        return userRepository.findAllByAnonymizedAtIsNullAndUnitIdOrderByUsernameAsc(unitId);
    }

    @Transactional(readOnly = true)
    public List<Long> parseNotificationUserIds(UnitDrivingLicenseSettings settings) {
        return parseIds(settings.getNotificationUserIds());
    }

    private String toJson(Long[] ids) {
        List<Long> list = new ArrayList<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null && id > 0 && !list.contains(id)) {
                    list.add(id);
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Long> parseIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Long> parsed = objectMapper.readValue(json, LONG_LIST);
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (Exception e) {
            return List.of();
        }
    }
}
