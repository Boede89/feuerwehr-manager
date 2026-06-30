package de.feuerwehr.manager.einsatzapp;

import de.feuerwehr.manager.divera.DiveraAlarmDetailsMapper.DiveraAlarmDetails;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.security.UserPermissionService;
import de.feuerwehr.manager.settings.AppModule;
import de.feuerwehr.manager.settings.ModuleSettingsService;
import de.feuerwehr.manager.unit.UnitRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EinsatzAppPushService {

    private final ModuleSettingsService moduleSettingsService;
    private final EinsatzAppSettingsService einsatzAppSettingsService;
    private final EinsatzappDeviceTokenRepository deviceTokenRepository;
    private final EinsatzappPushLogRepository pushLogRepository;
    private final FcmPushClient fcmPushClient;
    private final UserPermissionService userPermissionService;
    private final UnitRepository unitRepository;

    @Transactional
    public void tryDispatchFromWebhook(long unitId, DiveraAlarmDetails details) {
        if (details == null || details.alarmId() <= 0) {
            return;
        }
        if (details.closed()) {
            log.debug("[Einsatz-App] Push übersprungen — Einsatz {} geschlossen", details.alarmId());
            return;
        }
        if (!moduleSettingsService.isEnabled(AppModule.EINSATZAPP, unitId)) {
            return;
        }
        if (!einsatzAppSettingsService.isPushEnabled(unitId)) {
            return;
        }
        if (!fcmPushClient.isAvailable()) {
            log.debug("[Einsatz-App] Push übersprungen — FCM nicht konfiguriert (unit={})", unitId);
            return;
        }
        List<String> tokens = resolveTargetTokens(unitId);
        if (tokens.isEmpty()) {
            logPush(unitId, details, 0, 0, "Keine registrierten Geräte");
            return;
        }
        String title = details.title() != null && !details.title().isBlank() ? details.title().trim() : "Einsatz";
        String body = buildBody(details);
        FcmPushClient.FcmSendResult result = fcmPushClient.sendAlarmNotification(tokens, title, body, details.alarmId());
        if (!result.invalidTokens().isEmpty()) {
            deviceTokenRepository.deleteByFcmTokenIn(result.invalidTokens());
        }
        String error = result.successCount() == 0 && result.failureCount() > 0 ? "FCM-Versand fehlgeschlagen" : null;
        logPush(unitId, details, tokens.size(), result.successCount(), error);
        log.info(
                "[Einsatz-App] Push unit={} alarm={} targets={} sent={} failed={}",
                unitId,
                details.alarmId(),
                tokens.size(),
                result.successCount(),
                result.failureCount());
    }

    private List<String> resolveTargetTokens(long unitId) {
        List<String> tokens = new ArrayList<>();
        for (EinsatzappDeviceToken row : deviceTokenRepository.findByUnitId(unitId)) {
            if (row.getUser() == null || row.getFcmToken() == null || row.getFcmToken().isBlank()) {
                continue;
            }
            AppUserDetails actor = AppUserDetails.from(row.getUser());
            if (!userPermissionService.hasPermission(actor, unitId, "einsatzapp.read")) {
                continue;
            }
            tokens.add(row.getFcmToken().trim());
        }
        return tokens;
    }

    private static String buildBody(DiveraAlarmDetails details) {
        if (details.address() != null && !details.address().isBlank()) {
            return details.address().trim();
        }
        if (details.text() != null && !details.text().isBlank()) {
            String text = details.text().trim();
            return text.length() > 180 ? text.substring(0, 177) + "…" : text;
        }
        return "Neuer DIVERA-Einsatz";
    }

    private void logPush(
            long unitId, DiveraAlarmDetails details, int targeted, int sent, String errorMessage) {
        EinsatzappPushLog logEntry = new EinsatzappPushLog();
        logEntry.setUnit(unitRepository.getReferenceById(unitId));
        logEntry.setDiveraAlarmId(details.alarmId());
        logEntry.setAlarmTitle(details.title());
        logEntry.setTokensTargeted(targeted);
        logEntry.setTokensSent(sent);
        logEntry.setErrorMessage(errorMessage);
        pushLogRepository.save(logEntry);
    }
}
