package de.feuerwehr.manager.leitstellen;

import de.feuerwehr.manager.berichte.IncidentReport;
import de.feuerwehr.manager.berichte.IncidentReportAttachmentRepository;
import de.feuerwehr.manager.berichte.IncidentReportRepository;
import de.feuerwehr.manager.unit.Unit;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    static final Set<LeitstellenPollPhase> ACTIVE =
            EnumSet.of(LeitstellenPollPhase.WAITING_DEPESCHE, LeitstellenPollPhase.WAITING_ABSCHLUSS);

    private final LeitstellenMailPollSessionRepository sessionRepository;
    private final UnitLeitstellenMailSettingsRepository settingsRepository;
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
        int depescheWaitHours = Math.max(1, settings.getDepescheWaitHours());
        int abschlussWaitHours = Math.max(1, settings.getAbschlussWaitHours());

        for (LeitstellenMailPollSession session :
                sessionRepository.findActiveByUnitId(unitId, List.copyOf(ACTIVE))) {
            IncidentReport report = session.getIncidentReport();
            long reportId = report.getId();
            boolean hasDepesche = hasKind(reportId, LeitstellenMailKind.DEPESCHE);
            boolean hasAbschluss = hasKind(reportId, LeitstellenMailKind.ABSCHLUSS);

            if (hasDepesche && hasAbschluss) {
                complete(session, now, LeitstellenPollPhase.COMPLETED);
                continue;
            }

            Instant alarm = alarmInstant(report);
            Instant end = endInstant(report);
            Instant created = session.getCreatedAt() != null ? session.getCreatedAt() : now;

            if (!hasDepesche) {
                Instant depescheDeadline = (alarm != null ? alarm : created).plusSeconds(depescheWaitHours * 3600L);
                if (now.isAfter(depescheDeadline)) {
                    // Keine Depeche in der Wartezeit → Abruf beenden
                    complete(session, now, LeitstellenPollPhase.EXPIRED);
                    continue;
                }
                scheduleNext(session, now, LeitstellenPollPhase.WAITING_DEPESCHE, settings.getDepeschePollIntervalSeconds());
                continue;
            }

            // Depeche da, auf Abschluss warten — endet nach konfigurierter Zeit nach Einsatzende
            Instant abschlussAnchor = end != null ? end : (session.getLastPollAt() != null ? session.getLastPollAt() : created);
            Instant abschlussDeadline = abschlussAnchor.plusSeconds(abschlussWaitHours * 3600L);
            if (now.isAfter(abschlussDeadline)) {
                // Nur Depeche ist ok — Session erfolgreich beenden
                complete(session, now, LeitstellenPollPhase.COMPLETED);
                log.info(
                        "[Leitstellen-Mail] Abruf beendet (nur Depeche) unit={} report={} nach {}h Wartezeit",
                        unitId,
                        reportId,
                        abschlussWaitHours);
                continue;
            }
            scheduleNext(session, now, LeitstellenPollPhase.WAITING_ABSCHLUSS, settings.getAbschlussPollIntervalSeconds());
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
                "[Leitstellen-Mail] Poll-Session gestartet unit={} report={} phase={} "
                        + "(Depeche-Wartezeit {}h, Abschluss-Wartezeit {}h)",
                unit.getId(),
                report.getId(),
                phase,
                settings.getDepescheWaitHours(),
                settings.getAbschlussWaitHours());
    }

    private void complete(LeitstellenMailPollSession session, Instant now, LeitstellenPollPhase phase) {
        session.setPhase(phase);
        session.setCompletedAt(now);
        session.setLastPollAt(now);
        sessionRepository.save(session);
    }

    private void scheduleNext(
            LeitstellenMailPollSession session, Instant now, LeitstellenPollPhase phase, int intervalSec) {
        session.setPhase(phase);
        session.setLastPollAt(now);
        session.setNextPollAt(now.plusSeconds(Math.max(15, intervalSec)));
        session.setCompletedAt(null);
        sessionRepository.save(session);
    }

    private boolean hasKind(long reportId, LeitstellenMailKind kind) {
        return attachmentRepository.findByIncidentReportIdOrderByCreatedAtAsc(reportId).stream()
                .anyMatch(a -> kind.matchesFilename(a.getFilename()));
    }

    private static Instant alarmInstant(IncidentReport report) {
        if (report.getIncidentDate() == null) {
            return null;
        }
        LocalTime time = report.getAlarmTime() != null ? report.getAlarmTime() : LocalTime.MIDNIGHT;
        return LocalDateTime.of(report.getIncidentDate(), time).atZone(ZONE).toInstant();
    }

    private static Instant endInstant(IncidentReport report) {
        if (report.getIncidentDate() == null || report.getEndTime() == null) {
            return null;
        }
        LocalDateTime end = LocalDateTime.of(report.getIncidentDate(), report.getEndTime());
        if (report.getAlarmTime() != null && report.getEndTime().isBefore(report.getAlarmTime())) {
            end = end.plusDays(1);
        }
        return end.atZone(ZONE).toInstant();
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
