package de.feuerwehr.manager.dsgvo;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;
    private final DsgvoProperties dsgvoProperties;

    @Transactional
    public void record(
            AuditEventType type,
            Long actorUserId,
            Long subjectUserId,
            HttpServletRequest request,
            String detail) {
        AuditEvent event = new AuditEvent();
        event.setEventType(type);
        event.setActorUserId(actorUserId);
        event.setSubjectUserId(subjectUserId);
        event.setIpHash(hashFromRequest(request));
        event.setDetail(sanitizeDetail(detail));
        auditEventRepository.save(event);
    }

    @Transactional
    public void record(AuditEventType type, Long actorUserId, HttpServletRequest request) {
        record(type, actorUserId, actorUserId, request, null);
    }

    private String hashFromRequest(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getRemoteAddr();
        return PseudonymizationHelper.hashValue(dsgvoProperties.auditSalt(), ip);
    }

    public String auditSalt() {
        return dsgvoProperties.auditSalt();
    }

    public String hashUserAgent(String userAgent) {
        return PseudonymizationHelper.hashValue(dsgvoProperties.auditSalt(), userAgent);
    }

    private static String sanitizeDetail(String detail) {
        if (!StringUtils.hasText(detail)) {
            return null;
        }
        String trimmed = detail.trim();
        if (trimmed.length() > 500) {
            return trimmed.substring(0, 500);
        }
        return trimmed;
    }

    @Scheduled(cron = "${feuerwehr.dsgvo.audit-cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void purgeExpiredAuditEvents() {
        if (!dsgvoProperties.auditCleanupEnabled()) {
            return;
        }
        var cutoff = java.time.Instant.now().minusSeconds((long) dsgvoProperties.auditRetentionDays() * 86400L);
        int deleted = auditEventRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("DSGVO-Audit: {} Einträge älter als {} Tage gelöscht", deleted, dsgvoProperties.auditRetentionDays());
        }
    }
}
