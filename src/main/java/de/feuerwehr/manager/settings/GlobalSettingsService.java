package de.feuerwehr.manager.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GlobalSettingsService {

    private final ApplicationSettingsRepository settingsRepository;

    @Transactional(readOnly = true)
    public ApplicationSettings get() {
        return settings();
    }

    public boolean hasCustomLogo() {
        String logo = settings().getLogoBase64();
        return logo != null && !logo.isBlank();
    }

    @Transactional
    public void saveStammdaten(String ffName, String ffStrasse, String ffOrt, String appUrl, String feedbackEmail) {
        ApplicationSettings s = settings();
        s.setFfName(blankToNull(ffName));
        s.setFfStrasse(blankToNull(ffStrasse));
        s.setFfOrt(blankToNull(ffOrt));
        s.setAppUrl(normalizeUrl(appUrl));
        s.setFeedbackEmail(blankToNull(feedbackEmail));
        settingsRepository.save(s);
    }

    @Transactional
    public void savePrivacyContact(String name, String email, String phone, String hoster) {
        ApplicationSettings s = settings();
        s.setPrivacyContactName(blankToNull(name));
        s.setPrivacyContactEmail(blankToNull(email));
        s.setPrivacyContactPhone(blankToNull(phone));
        s.setPrivacyHoster(blankToNull(hoster));
        settingsRepository.save(s);
    }

    @Transactional
    public void saveLogoBase64(String dataUri) {
        ApplicationSettings s = settings();
        s.setLogoBase64(dataUri);
        settingsRepository.save(s);
    }

    @Transactional
    public void clearLogo() {
        ApplicationSettings s = settings();
        s.setLogoBase64(null);
        settingsRepository.save(s);
    }

    @Transactional
    public void saveSmtp(
            String host,
            Integer port,
            String username,
            String password,
            String fromEmail,
            String fromName,
            String encryption) {
        ApplicationSettings s = settings();
        s.setSmtpHost(blankToNull(host));
        s.setSmtpPort(port != null && port > 0 ? port : 587);
        s.setSmtpUsername(blankToNull(username));
        if (password != null && !password.isBlank()) {
            s.setSmtpPassword(password.trim());
        }
        s.setSmtpFromEmail(blankToNull(fromEmail));
        s.setSmtpFromName(blankToNull(fromName));
        s.setSmtpEncryption(blankToNull(encryption) != null ? encryption.trim() : "TLS");
        settingsRepository.save(s);
    }

    public boolean isSmtpPasswordConfigured() {
        String p = settings().getSmtpPassword();
        return p != null && !p.isBlank();
    }

    private ApplicationSettings settings() {
        return settingsRepository
                .findById(ApplicationSettings.SINGLETON_ID)
                .orElseGet(this::createDefault);
    }

    private ApplicationSettings createDefault() {
        ApplicationSettings settings = new ApplicationSettings();
        settings.setId(ApplicationSettings.SINGLETON_ID);
        settings.setTestModeEnabled(false);
        settings.setSmtpPort(587);
        settings.setSmtpEncryption("TLS");
        return settingsRepository.save(settings);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
