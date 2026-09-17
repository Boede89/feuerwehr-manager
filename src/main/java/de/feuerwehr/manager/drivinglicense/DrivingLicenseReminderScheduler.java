package de.feuerwehr.manager.drivinglicense;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DrivingLicenseReminderScheduler {

    private final DrivingLicenseReminderNotificationService reminderService;

    @Scheduled(cron = "${feuerwehr.driving-license.reminder-cron:0 15 7 * * *}")
    public void sendDailyReminders() {
        try {
            DrivingLicenseReminderNotificationService.ReminderRunResult result = reminderService.processAllUnits();
            if (result.sent() > 0 || result.failed() > 0) {
                log.info(
                        "Führerschein-Erinnerungen: {} gesendet, {} übersprungen, {} fehlgeschlagen",
                        result.sent(),
                        result.skipped(),
                        result.failed());
            }
        } catch (Exception e) {
            log.error("Führerschein-Erinnerungen konnten nicht versendet werden", e);
        }
    }
}
