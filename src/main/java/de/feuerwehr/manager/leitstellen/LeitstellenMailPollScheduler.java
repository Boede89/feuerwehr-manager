package de.feuerwehr.manager.leitstellen;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "feuerwehr.leitstellen-mail",
        name = "poll-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class LeitstellenMailPollScheduler {

    private final UnitLeitstellenMailSettingsRepository settingsRepository;
    private final LeitstellenMailImportService importService;

    @Scheduled(
            fixedDelayString = "${feuerwehr.leitstellen-mail.poll-interval-ms:120000}",
            initialDelayString = "${feuerwehr.leitstellen-mail.poll-initial-delay-ms:60000}")
    public void pollMailboxes() {
        int imported = 0;
        int units = 0;
        for (UnitLeitstellenMailSettings settings : settingsRepository.findAllEnabledWithHost()) {
            units++;
            try {
                LeitstellenMailImportService.PollResult result =
                        importService.pollUnit(settings.getUnit().getId());
                imported += result.importedAttachments();
            } catch (Exception e) {
                log.warn(
                        "[Leitstellen-Mail] Abruf unit={} fehlgeschlagen: {}",
                        settings.getUnit().getId(),
                        e.getMessage());
            }
        }
        if (imported > 0) {
            log.info("[Leitstellen-Mail] {} Einheit(en), {} Anhang/Anhänge importiert", units, imported);
        }
    }
}
