package de.feuerwehr.manager.atemschutz;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AtemschutzReminderScheduler {

    private final AtemschutzReminderNotificationService reminderService;

    @Scheduled(cron = "${feuerwehr.atemschutz.reminder-cron:0 0 7 * * *}")
    public void sendDailyReminders() {
        try {
            AtemschutzReminderNotificationService.ReminderRunResult result = reminderService.processAllUnits();
            if (result.sent() > 0 || result.failed() > 0) {
                log.info(
                        "Atemschutz-Erinnerungen: {} gesendet, {} übersprungen, {} fehlgeschlagen",
                        result.sent(),
                        result.skipped(),
                        result.failed());
            }
        } catch (Exception e) {
            log.error("Atemschutz-Erinnerungen konnten nicht versendet werden", e);
        }
    }
}
