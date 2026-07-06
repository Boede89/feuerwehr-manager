package de.feuerwehr.manager.einsatzapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "feuerwehr.einsatzapp",
        name = "alarm-poll-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EinsatzAppAlarmPollScheduler {

    private final EinsatzAppAlarmPollService alarmPollService;

    @Scheduled(
            fixedDelayString = "${feuerwehr.einsatzapp.alarm-poll-interval-ms:15000}",
            initialDelayString = "${feuerwehr.einsatzapp.alarm-poll-initial-delay-ms:15000}")
    public void pollForNewAlarms() {
        try {
            alarmPollService.pollAllUnits();
        } catch (Exception e) {
            log.warn("[Einsatz-App-Poll] Unerwarteter Fehler: {}", e.getMessage());
        }
    }
}
