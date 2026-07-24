package de.feuerwehr.manager.leitstellen;

import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeitstellenMailSettingsService {

    private final UnitLeitstellenMailSettingsRepository settingsRepository;
    private final UnitRepository unitRepository;

    @Transactional(readOnly = true)
    public Optional<UnitLeitstellenMailSettings> findByUnitId(long unitId) {
        return settingsRepository.findByUnitId(unitId);
    }

    @Transactional(readOnly = true)
    public UnitLeitstellenMailSettings getOrCreate(long unitId) {
        return settingsRepository.findByUnitId(unitId).orElseGet(() -> {
            Unit unit = unitRepository
                    .findById(unitId)
                    .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
            UnitLeitstellenMailSettings settings = new UnitLeitstellenMailSettings();
            settings.setUnit(unit);
            settings.setEnabled(false);
            settings.setImapEncryption("SSL");
            settings.setImapFolder("INBOX");
            settings.setImapPort(993);
            settings.setSubjectFilter("FAX");
            settings.setDepescheKeywords("depesche,alarmdepesche");
            settings.setAbschlussKeywords("abschluss,abschlussbericht");
            settings.setPollLookbackHours(24);
            settings.setMatchWindowHours(12);
            settings.setDepeschePollIntervalSeconds(60);
            settings.setAbschlussPollIntervalSeconds(300);
            settings.setUpdatedAt(Instant.now());
            return settingsRepository.save(settings);
        });
    }

    @Transactional
    public UnitLeitstellenMailSettings save(
            long unitId,
            boolean enabled,
            String imapHost,
            Integer imapPort,
            String imapUsername,
            String imapPassword,
            String imapEncryption,
            String imapFolder,
            String fromFilter,
            String subjectFilter,
            String depescheKeywords,
            String abschlussKeywords,
            Integer pollLookbackHours,
            Integer matchWindowHours,
            Integer depeschePollIntervalSeconds,
            Integer abschlussPollIntervalSeconds) {
        UnitLeitstellenMailSettings settings = getOrCreate(unitId);
        settings.setEnabled(enabled);
        settings.setImapHost(blankToNull(imapHost));
        settings.setImapPort(imapPort != null && imapPort > 0 ? imapPort : defaultPort(imapEncryption));
        settings.setImapUsername(blankToNull(imapUsername));
        if (imapPassword != null && !imapPassword.isBlank()) {
            settings.setImapPassword(imapPassword.trim());
        }
        settings.setImapEncryption(
                imapEncryption != null && !imapEncryption.isBlank() ? imapEncryption.trim().toUpperCase() : "SSL");
        settings.setImapFolder(
                imapFolder != null && !imapFolder.isBlank() ? imapFolder.trim() : "INBOX");
        settings.setFromFilter(blankToNull(fromFilter));
        // Leerer String = Filter absichtlich aus; null aus Formular = Default FAX nur bei Neuanlage
        if (subjectFilter != null) {
            settings.setSubjectFilter(blankToNull(subjectFilter));
        } else if (settings.getSubjectFilter() == null) {
            settings.setSubjectFilter("FAX");
        }
        settings.setDepescheKeywords(blankToDefault(depescheKeywords, "depesche,alarmdepesche"));
        settings.setAbschlussKeywords(blankToDefault(abschlussKeywords, "abschluss,abschlussbericht"));
        settings.setPollLookbackHours(
                pollLookbackHours != null && pollLookbackHours > 0 ? Math.min(pollLookbackHours, 720) : 24);
        settings.setMatchWindowHours(
                matchWindowHours != null && matchWindowHours > 0 ? Math.min(matchWindowHours, 72) : 12);
        settings.setDepeschePollIntervalSeconds(clampInterval(depeschePollIntervalSeconds, 60));
        settings.setAbschlussPollIntervalSeconds(clampInterval(abschlussPollIntervalSeconds, 300));
        settings.setUpdatedAt(Instant.now());
        return settingsRepository.save(settings);
    }

    /** 15 s … 1 h */
    private static int clampInterval(Integer seconds, int defaultSeconds) {
        if (seconds == null || seconds <= 0) {
            return defaultSeconds;
        }
        return Math.min(3600, Math.max(15, seconds));
    }

    @Transactional(readOnly = true)
    public boolean isPasswordConfigured(long unitId) {
        return settingsRepository
                .findByUnitId(unitId)
                .map(s -> s.getImapPassword() != null && !s.getImapPassword().isBlank())
                .orElse(false);
    }

    private static int defaultPort(String encryption) {
        return "TLS".equalsIgnoreCase(encryption) ? 143 : 993;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String blankToDefault(String value, String defaultValue) {
        String trimmed = blankToNull(value);
        return trimmed != null ? trimmed : defaultValue;
    }
}
