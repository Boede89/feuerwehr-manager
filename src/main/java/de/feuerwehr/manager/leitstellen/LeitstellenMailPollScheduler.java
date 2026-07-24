package de.feuerwehr.manager.leitstellen;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fragt nur Postfächer ab, für die eine aktive Poll-Session fällig ist
 * (nach DIVERA-Einsatz: kurzes Intervall bis Depeche, dann längeres bis Abschluss).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "feuerwehr.leitstellen-mail",
        name = "poll-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class LeitstellenMailPollScheduler {

    private final LeitstellenMailPollSessionService sessionService;
    private final LeitstellenMailPollRunner pollRunner;

    @Scheduled(
            fixedDelayString = "${feuerwehr.leitstellen-mail.poll-tick-ms:15000}",
            initialDelayString = "${feuerwehr.leitstellen-mail.poll-initial-delay-ms:60000}")
    public void pollDueSessions() {
        List<Long> unitIds = sessionService.findUnitIdsDue(Instant.now());
        if (unitIds.isEmpty()) {
            return;
        }
        int imported = 0;
        for (Long unitId : unitIds) {
            try {
                LeitstellenMailImportService.PollResult result = pollRunner.pollUnitAndRefresh(unitId);
                imported += result.importedAttachments();
            } catch (Exception e) {
                log.warn("[Leitstellen-Mail] Abruf unit={} fehlgeschlagen: {}", unitId, e.getMessage());
            }
        }
        if (imported > 0) {
            log.info("[Leitstellen-Mail] {} Einheit(en) fällig, {} Anhang/Anhänge importiert", unitIds.size(), imported);
        }
    }
}
