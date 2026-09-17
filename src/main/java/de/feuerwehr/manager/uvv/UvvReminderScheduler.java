package de.feuerwehr.manager.uvv;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UvvReminderScheduler {

    private final UvvReminderNotificationService reminderService;

    @Scheduled(cron = "${feuerwehr.uvv.reminder-cron:0 30 7 * * *}")
    public void sendDailyReminders() {
        try {
            UvvReminderNotificationService.ReminderRunResult result = reminderService.processAllUnits();
            if (result.sent() > 0 || result.failed() > 0) {
                log.info(
                        "UVV-Erinnerungen: {} gesendet, {} übersprungen, {} fehlgeschlagen",
                        result.sent(),
                        result.skipped(),
                        result.failed());
            }
        } catch (Exception e) {
            log.error("UVV-Erinnerungen konnten nicht versendet werden", e);
        }
    }
}
