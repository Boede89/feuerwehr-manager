package de.feuerwehr.manager.dsgvo;

import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PrivacyService {

    private final PrivacyNoticeRepository noticeRepository;
    private final PrivacyConsentRepository consentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public Optional<PrivacyNotice> getCurrentNotice() {
        return noticeRepository.findTopByOrderByActiveFromDesc();
    }

    public boolean needsConsent(User user) {
        if (user == null || user.getAnonymizedAt() != null) {
            return false;
        }
        Optional<PrivacyNotice> current = getCurrentNotice();
        if (current.isEmpty()) {
            return false;
        }
        String required = current.get().getVersion();
        return user.getPrivacyNoticeVersion() == null || !required.equals(user.getPrivacyNoticeVersion());
    }

    @Transactional
    public void recordConsent(User user, HttpServletRequest request, String userAgent) {
        PrivacyNotice notice = getCurrentNotice()
                .orElseThrow(() -> new IllegalStateException("Kein Datenschutzhinweis hinterlegt"));
        user.setPrivacyNoticeVersion(notice.getVersion());
        user.setPrivacyNoticeAcceptedAt(java.time.Instant.now());
        userRepository.save(user);

        PrivacyConsent consent = new PrivacyConsent();
        consent.setUser(user);
        consent.setNoticeVersion(notice.getVersion());
        if (request != null) {
            consent.setIpHash(PseudonymizationHelper.hashValue(
                    auditService.auditSalt(), request.getRemoteAddr()));
        }
        consent.setUserAgentHash(auditService.hashUserAgent(userAgent));
        consentRepository.save(consent);

        auditService.record(AuditEventType.PRIVACY_CONSENT, user.getId(), request);
    }
}
