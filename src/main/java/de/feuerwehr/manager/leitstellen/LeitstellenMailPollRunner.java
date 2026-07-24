package de.feuerwehr.manager.leitstellen;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Vermeidet Zyklen zwischen Import- und Session-Service und bündelt Abruf + Session-Update.
 */
@Service
@Slf4j
public class LeitstellenMailPollRunner {

    private final LeitstellenMailImportService importService;
    private final LeitstellenMailPollSessionService sessionService;

    public LeitstellenMailPollRunner(
            LeitstellenMailImportService importService, @Lazy LeitstellenMailPollSessionService sessionService) {
        this.importService = importService;
        this.sessionService = sessionService;
    }

    public LeitstellenMailImportService.PollResult pollUnitAndRefresh(long unitId) {
        LeitstellenMailImportService.PollResult result = importService.pollUnit(unitId);
        try {
            sessionService.refreshAfterPoll(unitId);
        } catch (Exception e) {
            log.warn("[Leitstellen-Mail] Session-Update unit={} fehlgeschlagen: {}", unitId, e.getMessage());
        }
        return result;
    }

    public LeitstellenMailImportService.PollResult pollReportAndRefresh(long unitId, long reportId) {
        LeitstellenMailImportService.PollResult result = importService.pollForReport(unitId, reportId);
        try {
            sessionService.refreshAfterPoll(unitId);
        } catch (Exception e) {
            log.warn("[Leitstellen-Mail] Session-Update unit={} fehlgeschlagen: {}", unitId, e.getMessage());
        }
        return result;
    }
}
