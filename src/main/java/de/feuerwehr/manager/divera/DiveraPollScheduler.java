package de.feuerwehr.manager.divera;

import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional: erkennt neue Divera-Einsätze per Polling und loggt sie.
 * Später: hier ansetzen für FCM / Web-Push / Benachrichtigungs-Outbox.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "feuerwehr.divera", name = "poll-enabled", havingValue = "true")
public class DiveraPollScheduler {

    private final DiveraService diveraService;
    private final AtomicLong lastMaxAlarmId = new AtomicLong(0);

    @Scheduled(fixedDelayString = "${feuerwehr.divera.poll-interval-ms:120000}", initialDelayString = "30000")
    public void pollDemoUnit() {
        long unitId = 1L;
        DiveraAlarmsResponse r = diveraService.getAlarmsForUnit(unitId);
        if (!r.success()) {
            return;
        }
        long maxId = r.alarms().stream().mapToLong(DiveraAlarmSummary::id).max().orElse(0);
        long prev = lastMaxAlarmId.get();
        if (maxId > 0 && prev > 0 && maxId > prev) {
            log.info("[Divera] Neuer Einsatz erkannt (Push später): alarmId={}", maxId);
        }
        lastMaxAlarmId.updateAndGet(x -> Math.max(x, maxId));
    }
}
