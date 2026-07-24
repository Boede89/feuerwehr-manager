package de.feuerwehr.manager.leitstellen;

import de.feuerwehr.manager.berichte.IncidentReport;
import de.feuerwehr.manager.berichte.IncidentReportAttachmentRepository;
import de.feuerwehr.manager.berichte.IncidentReportRepository;
import de.feuerwehr.manager.unit.Unit;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeitstellenMailPollSessionService {

    static final Set<LeitstellenPollPhase> ACTIVE =
            EnumSet.of(LeitstellenPollPhase.WAITING_DEPESCHE, LeitstellenPollPhase.WAITING_ABSCHLUSS);

    private final LeitstellenMailPollSessionRepository sessionRepository;
    private final UnitLeitstellenMailSettingsRepository settingsRepository;
    private final LeitstellenMailImportRepository importRepository;
    private final IncidentReportAttachmentRepository attachmentRepository;
    private final IncidentReportRepository incidentReportRepository;
    private final LeitstellenMailPollRunner pollRunner;

    @Transactional
    public void startAfterDiveraCreate(long unitId, long reportId) {
        UnitLeitstellenMailSettings settings = settingsRepository.findByUnitId(unitId).orElse(null);
        if (settings == null || !settings.isEnabled()
                || settings.getImapHost() == null
                || settings.getImapHost().isBlank()) {
            return;
        }
        IncidentReport report = incidentReportRepository.findById(reportId).orElse(null);
        if (report == null || report.getUnit() == null || report.getUnit().getId() != unitId) {
            return;
        }
        upsertActiveSession(settings.getUnit(), report, settings, Instant.now());
        runPollAfterCommit(unitId);
    }

    @Transactional
    public void refreshAfterPoll(long unitId) {
        UnitLeitstellenMailSettings settings = settingsRepository.findByUnitId(unitId).orElse(null);
        if (settings == null) {
            return;
        }
        Instant now = Instant.now();
        long lookbackSeconds = Math.max(1, settings.getPollLookbackHours()) * 3600L;
        for (LeitstellenMailPollSession session :
                sessionRepository.findActiveByUnitId(unitId, List.copyOf(ACTIVE))) {
            Instant created = session.getCreatedAt() != null ? session.getCreatedAt() : now;
            if (created.plusSeconds(lookbackSeconds).isBefore(now)) {
                session.setPhase(LeitstellenPollPhase.EXPIRED);
                session.setCompletedAt(now);
                session.setLastPollAt(now);
                sessionRepository.save(session);
                continue;
            }
            long reportId = session.getIncidentReport().getId();
            boolean hasDepesche = hasKind(reportId, LeitstellenMailKind.DEPESCHE);
            boolean hasAbschluss = hasKind(reportId, LeitstellenMailKind.ABSCHLUSS);
            if (hasDepesche && hasAbschluss) {
                session.setPhase(LeitstellenPollPhase.COMPLETED);
                session.setCompletedAt(now);
                session.setLastPollAt(now);
                sessionRepository.save(session);
                continue;
            }
            LeitstellenPollPhase phase =
                    hasDepesche ? LeitstellenPollPhase.WAITING_ABSCHLUSS : LeitstellenPollPhase.WAITING_DEPESCHE;
            int intervalSec = phase == LeitstellenPollPhase.WAITING_ABSCHLUSS
                    ? settings.getAbschlussPollIntervalSeconds()
                    : settings.getDepeschePollIntervalSeconds();
            session.setPhase(phase);
            session.setLastPollAt(now);
            session.setNextPollAt(now.plusSeconds(Math.max(15, intervalSec)));
            session.setCompletedAt(null);
            sessionRepository.save(session);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> findUnitIdsDue(Instant now) {
        return sessionRepository.findUnitIdsDue(List.copyOf(ACTIVE), now);
    }

    private void upsertActiveSession(
            Unit unit, IncidentReport report, UnitLeitstellenMailSettings settings, Instant now) {
        LeitstellenMailPollSession session = sessionRepository
                .findByIncidentReportId(report.getId())
                .orElseGet(LeitstellenMailPollSession::new);
        boolean hasDepesche = hasKind(report.getId(), LeitstellenMailKind.DEPESCHE);
        boolean hasAbschluss = hasKind(report.getId(), LeitstellenMailKind.ABSCHLUSS);
        if (hasDepesche && hasAbschluss) {
            session.setUnit(unit);
            session.setIncidentReport(report);
            session.setPhase(LeitstellenPollPhase.COMPLETED);
            session.setCompletedAt(now);
            session.setNextPollAt(now);
            session.setLastPollAt(now);
            if (session.getCreatedAt() == null) {
                session.setCreatedAt(now);
            }
            sessionRepository.save(session);
            return;
        }
        LeitstellenPollPhase phase =
                hasDepesche ? LeitstellenPollPhase.WAITING_ABSCHLUSS : LeitstellenPollPhase.WAITING_DEPESCHE;
        session.setUnit(unit);
        session.setIncidentReport(report);
        session.setPhase(phase);
        session.setNextPollAt(now);
        session.setCompletedAt(null);
        if (session.getCreatedAt() == null) {
            session.setCreatedAt(now);
        }
        sessionRepository.save(session);
        log.info(
                "[Leitstellen-Mail] Poll-Session gestartet unit={} report={} phase={} (Depeche alle {}s, Abschluss alle {}s)",
                unit.getId(),
                report.getId(),
                phase,
                settings.getDepeschePollIntervalSeconds(),
                settings.getAbschlussPollIntervalSeconds());
    }

    private boolean hasKind(long reportId, LeitstellenMailKind kind) {
        return attachmentRepository
                .findFirstByIncidentReportIdAndFilenameIgnoreCase(reportId, kind.storedFilename())
                .isPresent();
    }

    private void runPollAfterCommit(long unitId) {
        Runnable poll = () -> {
            try {
                pollRunner.pollUnitAndRefresh(unitId);
            } catch (Exception e) {
                log.warn(
                        "[Leitstellen-Mail] Sofort-Abruf nach DIVERA unit={} fehlgeschlagen: {}",
                        unitId,
                        e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    startBackgroundPoll(unitId, poll);
                }
            });
        } else {
            startBackgroundPoll(unitId, poll);
        }
    }

    private static void startBackgroundPoll(long unitId, Runnable poll) {
        Thread t = new Thread(poll, "leitstellen-poll-" + unitId);
        t.setDaemon(true);
        t.start();
    }
}
